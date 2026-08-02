package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.PreparationRecipeDao
import com.miara.cuentame.core.database.dao.ProductionBatchDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionBatchPostingCoordinator @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val batchDao: ProductionBatchDao,
    private val recipeDao: PreparationRecipeDao,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val areaDao: InventoryAreaDao,
    private val movementDao: InventoryMovementDao,
    private val snapshotService: InventorySnapshotService,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val historyValidator: ProductionBatchMovementHistoryValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val failureBoundary: IntegrationFailureBoundary
) {
    suspend fun post(
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

            if (batch.status == DocumentStatus.POSTED.name) {
                historyValidator.validatePostedHistory(batch, components, existingMovements)
                return@withTransaction
            }

            if (batch.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }

            historyValidator.validateDraftHistory(batch, existingMovements)

            // Revalidate references
            val recipe = recipeDao.getById(batch.recipeId)
            if (recipe == null || recipe.status != PreparationRecipeStatus.ACTIVE.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeNotActive))
            }

            val outputIngredient = ingredientDao.getById(batch.outputIngredientId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientNotFound))
            if (!outputIngredient.isActive || outputIngredient.deletedAt != null) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientInactive))
            }

            val outputArea = areaDao.getById(batch.outputAreaId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaNotFound))
            if (!outputArea.isActive || outputArea.deletedAt != null || outputArea.restaurantId != restaurantId.value) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaInactive))
            }

            val outputUnitOption = unitOptionDao.getById(batch.outputUnitOptionId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionNotFound))
            if (!outputUnitOption.isActive || outputUnitOption.deletedAt != null || outputUnitOption.ingredientId != batch.outputIngredientId) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionInactive))
            }

            // Recalculate canonical quantities and costs
            val effectiveAt = Instant.ofEpochMilli(batch.effectiveAt)
            var totalComponentCost = BigDecimal.ZERO

            val updatedComponents = components.map { component ->
                val sourceAreaId = component.sourceAreaId
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaNotFound))

                val componentIngredient = ingredientDao.getById(component.componentIngredientId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientNotFound))
                if (!componentIngredient.isActive || componentIngredient.deletedAt != null) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientInactive))
                }

                val sourceArea = areaDao.getById(sourceAreaId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaNotFound))
                if (!sourceArea.isActive || sourceArea.deletedAt != null || sourceArea.restaurantId != restaurantId.value) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaInactive))
                }

                val unitOption = unitOptionDao.getById(component.unitOptionId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentUnitOptionNotFound))
                if (!unitOption.isActive || unitOption.deletedAt != null || unitOption.ingredientId != component.componentIngredientId) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentUnitOptionInactive))
                }

                val snapshot = snapshotService.calculateAt(
                    restaurantId,
                    IngredientId(component.componentIngredientId),
                    InventoryAreaId(sourceAreaId),
                    effectiveAt
                )

                val averageCost = snapshot.ingredientAverageCostBase
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentCostUnavailable))

                val actualQuantityBase = BigDecimal(component.actualQuantityEntered).multiply(unitOption.factorToBase)
                val componentTotalCost = actualQuantityBase.multiply(averageCost)
                totalComponentCost = totalComponentCost.add(componentTotalCost)

                component.copy(
                    actualQuantityBase = actualQuantityBase.toPlainString(),
                    unitCostBaseSnapshot = averageCost.toPlainString(),
                    totalCostSnapshot = componentTotalCost.toPlainString(),
                    updatedAt = timeProvider.now().toEpochMilli()
                )
            }

            val actualOutputQuantityBase = BigDecimal(batch.actualOutputQuantityEntered).multiply(outputUnitOption.factorToBase)
            val outputUnitCostBase = if (actualOutputQuantityBase.compareTo(BigDecimal.ZERO) > 0) {
                totalComponentCost.divide(actualOutputQuantityBase, MathContext.DECIMAL128)
            } else {
                BigDecimal.ZERO
            }

            val updatedBatch = batch.copy(
                actualOutputQuantityBase = actualOutputQuantityBase.toPlainString(),
                totalComponentCostSnapshot = totalComponentCost.toPlainString(),
                outputUnitCostBaseSnapshot = outputUnitCostBase.toPlainString(),
                status = DocumentStatus.POSTED.name,
                postedAt = timeProvider.now().toEpochMilli(),
                updatedAt = timeProvider.now().toEpochMilli()
            )

            // Persist snapshots
            batchDao.update(updatedBatch)
            updatedComponents.forEach { batchDao.updateComponent(it) }

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_SNAPSHOTS)

            // Insert Movements
            val movements = updatedComponents.map { component ->
                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = restaurantId.value,
                    ingredientId = component.componentIngredientId,
                    areaId = component.sourceAreaId!!,
                    movementType = InventoryMovementType.PRODUCTION_CONSUMPTION.name,
                    quantityBaseSigned = BigDecimal(component.actualQuantityBase).negate().toPlainString(),
                    unitCostBaseSnapshot = component.unitCostBaseSnapshot,
                    totalValueSnapshot = component.totalCostSnapshot?.let { BigDecimal(it).negate().toPlainString() },
                    effectiveAt = batch.effectiveAt,
                    sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
                    sourceDocumentId = batch.id,
                    sourceOperationId = "production-post:${batch.id}:consume:${component.id}",
                    sourceLineId = component.id,
                    reversalOfMovementId = null,
                    createdAt = timeProvider.now().toEpochMilli()
                )
            }
            movementDao.insertAll(movements)

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_CONSUMPTION)

            val outputMovement = InventoryMovementEntity(
                id = idGenerator.newId(),
                restaurantId = restaurantId.value,
                ingredientId = batch.outputIngredientId,
                areaId = batch.outputAreaId,
                movementType = InventoryMovementType.PRODUCTION_OUTPUT.name,
                quantityBaseSigned = updatedBatch.actualOutputQuantityBase,
                unitCostBaseSnapshot = updatedBatch.outputUnitCostBaseSnapshot,
                totalValueSnapshot = updatedBatch.totalComponentCostSnapshot,
                effectiveAt = batch.effectiveAt,
                sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
                sourceDocumentId = batch.id,
                sourceOperationId = "production-post:${batch.id}:output",
                sourceLineId = batch.id,
                reversalOfMovementId = null,
                createdAt = timeProvider.now().toEpochMilli()
            )
            movementDao.insert(outputMovement)

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_OUTPUT)

            // Rebuild projections
            val affectedIngredients = (updatedComponents.map { it.componentIngredientId } + batch.outputIngredientId).distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_PROJECTIONS)
        }
    }
}
