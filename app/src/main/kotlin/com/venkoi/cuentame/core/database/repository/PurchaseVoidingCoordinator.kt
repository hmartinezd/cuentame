package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.repository.IntegrationFailurePoints
import com.venkoi.cuentame.core.common.ids.IdGenerator
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.database.dao.InventoryMovementDao
import com.venkoi.cuentame.core.database.dao.PurchaseDao
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseVoidingCoordinator @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val movementDao: InventoryMovementDao,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val referenceValidator: PurchaseReferenceValidator,
    private val historyValidator: PurchaseMovementHistoryValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val failureBoundary: IntegrationFailureBoundary
) {
    suspend fun void(
        receiptId: PurchaseReceiptId,
        activeRestaurant: RestaurantEntity
    ) {
        val receipt = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)
        val lines = purchaseDao.getLinesForReceipt(receiptId.value)
        val allMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)

        if (receipt.status == DocumentStatus.VOIDED.name) {
            historyValidator.validateVoidedHistory(receipt, lines, allMovements)
            return
        }

        if (receipt.status != DocumentStatus.POSTED.name) {
            throw ValidationError.InvalidPurchaseStatusTransition
        }

        historyValidator.validatePostedHistory(receipt, lines, allMovements)

        val originalMovements = allMovements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
        val now = timeProvider.now().toEpochMilli()
        val reversals = originalMovements.map { original ->
            InventoryMovementEntity(
                id = idGenerator.newId(),
                restaurantId = activeRestaurant.id,
                ingredientId = original.ingredientId,
                areaId = original.areaId,
                movementType = InventoryMovementType.REVERSAL.name,
                quantityBaseSigned = BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
                unitCostBaseSnapshot = original.unitCostBaseSnapshot,
                totalValueSnapshot = original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
                effectiveAt = now,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = receipt.id,
                sourceOperationId = "reversal:${original.id}",
                sourceLineId = original.sourceLineId,
                reversalOfMovementId = original.id,
                createdAt = now
            )
        }

        movementDao.insertAll(reversals)
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_VOID_AFTER_REVERSALS)

        val affectedIngredients = lines.map { it.ingredientId }.distinct()
        affectedIngredients.forEach { ingredientId ->
            projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
        }
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_VOID_AFTER_PROJECTIONS)

        purchaseDao.updateReceipt(receipt.copy(
            status = DocumentStatus.VOIDED.name,
            voidedAt = now,
            updatedAt = now
        ))
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_VOID_AFTER_MARK_VOIDED)
    }
}
