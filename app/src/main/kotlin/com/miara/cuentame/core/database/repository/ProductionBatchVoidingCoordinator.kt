package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.ProductionBatchDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionBatchVoidingCoordinator @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val batchDao: ProductionBatchDao,
    private val movementDao: InventoryMovementDao,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val historyValidator: ProductionBatchMovementHistoryValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val failureBoundary: IntegrationFailureBoundary
) {
    suspend fun void(
        batchId: ProductionBatchId,
        restaurantId: RestaurantId
    ) {
        database.withTransaction {
            val batch = batchDao.getById(batchId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))

            if (batch.restaurantId != restaurantId.value) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RestaurantMismatch))
            }

            val components = batchDao.getComponents(batch.id)
            val existingMovements = movementDao.getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batch.id)

            if (batch.status == DocumentStatus.VOIDED.name) {
                try {
                    historyValidator.validateVoidedHistory(batch, components, existingMovements)
                } catch (_: ValidationError.MalformedProductionMovementHistory) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MovementHistoryConflict))
                }
                return@withTransaction
            }

            if (batch.status != DocumentStatus.POSTED.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MovementHistoryConflict))
            }

            try {
                historyValidator.validatePostedHistory(batch, components, existingMovements)
            } catch (_: ValidationError.MalformedProductionMovementHistory) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MovementHistoryConflict))
            }

            val voidedAt = timeProvider.now().toEpochMilli()

            val reversals = existingMovements.map { original ->
                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = restaurantId.value,
                    ingredientId = original.ingredientId,
                    areaId = original.areaId,
                    movementType = InventoryMovementType.REVERSAL.name,
                    quantityBaseSigned = BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
                    unitCostBaseSnapshot = original.unitCostBaseSnapshot,
                    totalValueSnapshot = original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
                    effectiveAt = voidedAt,
                    sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
                    sourceDocumentId = batch.id,
                    sourceOperationId = "reversal:${original.id}",
                    sourceLineId = original.sourceLineId,
                    reversalOfMovementId = original.id,
                    createdAt = voidedAt
                )
            }

            movementDao.insertAll(reversals)

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_REVERSALS)

            val affectedIngredients = (components.map { it.componentIngredientId } + batch.outputIngredientId).distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_PROJECTIONS)

            val voidedBatch = batch.copy(
                status = DocumentStatus.VOIDED.name,
                voidedAt = voidedAt,
                updatedAt = voidedAt
            )
            batchDao.update(voidedBatch)

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_MARK_VOIDED)
        }
    }
}
