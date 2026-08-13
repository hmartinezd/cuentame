package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.repository.IntegrationFailurePoints
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.PurchaseOcrDao
import com.miara.cuentame.core.database.dao.PurchaseInvoiceMaterializationDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.purchase.DuplicateInvoicePostingException
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchasePostingCoordinator @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val ocrDao: PurchaseOcrDao,
    private val materializationDao: PurchaseInvoiceMaterializationDao,
    private val duplicateInvoiceDetector: DuplicateInvoiceDetector,
    private val movementDao: InventoryMovementDao,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val referenceValidator: PurchaseReferenceValidator,
    private val lineCalculator: PurchaseLineCalculator,
    private val historyValidator: PurchaseMovementHistoryValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val failureBoundary: IntegrationFailureBoundary
) {
    suspend fun post(
        receiptId: PurchaseReceiptId,
        activeRestaurant: RestaurantEntity
    ) {
        val receipt = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)
        val lines = purchaseDao.getLinesForReceipt(receiptId.value)
        val existingMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)

        if (receipt.status == DocumentStatus.POSTED.name) {
            historyValidator.validatePostedHistory(receipt, lines, existingMovements)
            return
        }

        if (receipt.status != DocumentStatus.DRAFT.name) {
            throw ValidationError.InvalidPurchaseStatusTransition
        }

        historyValidator.validateDraftHistory(receipt, existingMovements)
        if (lines.isEmpty()) throw ValidationError.PurchaseHasNoLines

        referenceValidator.validateSupplierForPosting(receipt.supplierId, activeRestaurant.id)

        // Authoritative second admission check inside the posting transaction.
        val sourceSha = ocrDao.getOcrResultForReceiptSync(receipt.id)?.sourceDocumentSha256
        val duplicate = duplicateInvoiceDetector.find(
            activeRestaurant.id, receipt.id, receipt.supplierId, receipt.invoiceNumber, sourceSha
        )
        if (duplicate != null) {
            val application = materializationDao.getApplicationForReceipt(receipt.id)
            val accepted = duplicate.matchesOverride(
                application?.duplicateOverrideType,
                application?.duplicateExistingReceiptId,
                application?.duplicateNormalizedInvoiceNumber,
                application?.duplicateSourceSha256
            )
            if (!accepted) throw DuplicateInvoicePostingException(duplicate)
        }

        val movements = lines.map { lineEntity ->
            val lineRefs = referenceValidator.validateLineReferences(
                activeRestaurant.id,
                IngredientId(lineEntity.ingredientId),
                InventoryAreaId(lineEntity.areaId),
                IngredientUnitOptionId(lineEntity.ingredientUnitOptionId),
                requireActive = true
            )

            val calculation = lineCalculator.calculate(
                quantityEntered = BigDecimal(lineEntity.quantityEntered),
                lineTotal = BigDecimal(lineEntity.lineTotal),
                optionFactorToBase = lineRefs.unitOption.factorToBase
            )

            purchaseDao.updateLine(lineEntity.copy(
                quantityBase = calculation.quantityBase.toPlainString(),
                unitCostBase = calculation.unitCostBase.toPlainString(),
                updatedAt = timeProvider.now().toEpochMilli()
            ))

            InventoryMovementEntity(
                id = idGenerator.newId(),
                restaurantId = activeRestaurant.id,
                ingredientId = lineEntity.ingredientId,
                areaId = lineEntity.areaId,
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = calculation.quantityBase.toPlainString(),
                unitCostBaseSnapshot = calculation.unitCostBase.toPlainString(),
                totalValueSnapshot = lineEntity.lineTotal,
                effectiveAt = receipt.purchaseDate,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = receipt.id,
                sourceOperationId = InventoryMovementOperationIds.purchasePost(receipt.id, lineEntity.id),
                sourceLineId = lineEntity.id,
                reversalOfMovementId = null,
                createdAt = timeProvider.now().toEpochMilli()
            )
        }

        movementDao.insertAll(movements)
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_POST_AFTER_MOVEMENTS)

        val affectedIngredients = lines.map { it.ingredientId }.distinct()
        affectedIngredients.forEach { ingredientId ->
            projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
        }
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_POST_AFTER_PROJECTIONS)

        val now = timeProvider.now().toEpochMilli()
        purchaseDao.updateReceipt(receipt.copy(
            status = DocumentStatus.POSTED.name,
            postedAt = now,
            updatedAt = now
        ))
        
        failureBoundary.trigger(IntegrationFailurePoints.PURCHASE_POST_AFTER_MARK_POSTED)
    }
}
