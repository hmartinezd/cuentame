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
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ValidationError
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
                try {
                    historyValidator.validatePostedHistory(batch, components, existingMovements)
                } catch (_: ValidationError.MalformedProductionMovementHistory) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MovementHistoryConflict))
                }
                return@withTransaction
            }

            if (batch.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }

            historyValidator.validateDraftHistory(batch, existingMovements)

            if (components.isEmpty()) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeHasNoComponents))
            }

            val batchMultiplier = try {
                BigDecimal(batch.batchMultiplier)
            } catch (_: Exception) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
            }

            if (batchMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
            }

            val now = timeProvider.now()
            val postedAtMs = now.toEpochMilli()

            if (batch.effectiveAt > postedAtMs) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.EffectiveTimeInFuture))
            }

            // Revalidate references
            val recipe = recipeDao.getById(batch.recipeId)
            if (recipe == null || recipe.status != PreparationRecipeStatus.ACTIVE.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeNotActive))
            }
            if (recipe.restaurantId != restaurantId.value) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RestaurantMismatch))
            }

            val outputIngredient = ingredientDao.getById(batch.outputIngredientId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientNotFound))
            if (!outputIngredient.isActive || outputIngredient.deletedAt != null || outputIngredient.restaurantId != restaurantId.value) {
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
            if (outputUnitOption.factorToBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.InvalidUnitFactor))
            }

            val actualOutputQuantityEntered = try { BigDecimal(batch.actualOutputQuantityEntered) } catch(_: Exception) { BigDecimal.ZERO }
            if (actualOutputQuantityEntered.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ActualOutputMustBePositive))
            }
            
            val actualOutputQuantityBase = actualOutputQuantityEntered.multiply(outputUnitOption.factorToBase)
            if (actualOutputQuantityBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ActualOutputMustBePositive))
            }

            // Recalculate canonical quantities and costs
            val effectiveAt = Instant.ofEpochMilli(batch.effectiveAt)
            var totalComponentCost = BigDecimal.ZERO

            val updatedComponents = components.map { component ->
                val sourceAreaId = component.sourceAreaId
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaNotFound))

                val componentIngredient = ingredientDao.getById(component.componentIngredientId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentIngredientNotFound))
                if (!componentIngredient.isActive || componentIngredient.deletedAt != null) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentIngredientInactive))
                }
                if (componentIngredient.restaurantId != restaurantId.value) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentIngredientRestaurantMismatch))
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
                if (unitOption.factorToBase.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.InvalidUnitFactor))
                }

                val actualQtyEntered = try { BigDecimal(component.actualQuantityEntered) } catch(_: Exception) { BigDecimal.ZERO }
                if (actualQtyEntered.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentQuantityMustBePositive))
                }

                val actualQtyBase = actualQtyEntered.multiply(unitOption.factorToBase)
                if (actualQtyBase.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentQuantityMustBePositive))
                }

                val snapshot = snapshotService.calculateAt(
                    restaurantId,
                    IngredientId(component.componentIngredientId),
                    InventoryAreaId(sourceAreaId),
                    effectiveAt
                )

                val averageCost = snapshot.ingredientAverageCostBase
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentCostUnavailable))

                val componentTotalCost = actualQtyBase.multiply(averageCost)
                totalComponentCost = totalComponentCost.add(componentTotalCost)

                component.copy(
                    actualQuantityBase = actualQtyBase.toPlainString(),
                    unitCostBaseSnapshot = averageCost.toPlainString(),
                    totalCostSnapshot = componentTotalCost.toPlainString(),
                    updatedAt = postedAtMs
                )
            }

            val outputUnitCostBase = totalComponentCost.divide(actualOutputQuantityBase, MathContext.DECIMAL128)

            val snapshotBatch = batch.copy(
                actualOutputQuantityBase = actualOutputQuantityBase.toPlainString(),
                totalComponentCostSnapshot = totalComponentCost.toPlainString(),
                outputUnitCostBaseSnapshot = outputUnitCostBase.toPlainString(),
                updatedAt = postedAtMs
            )

            // Persist snapshots
            batchDao.update(snapshotBatch)
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
                    createdAt = postedAtMs
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
                quantityBaseSigned = snapshotBatch.actualOutputQuantityBase,
                unitCostBaseSnapshot = snapshotBatch.outputUnitCostBaseSnapshot,
                totalValueSnapshot = snapshotBatch.totalComponentCostSnapshot,
                effectiveAt = batch.effectiveAt,
                sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
                sourceDocumentId = batch.id,
                sourceOperationId = "production-post:${batch.id}:output",
                sourceLineId = batch.id,
                reversalOfMovementId = null,
                createdAt = postedAtMs
            )
            movementDao.insert(outputMovement)

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_OUTPUT)

            // Rebuild projections
            val affectedIngredients = (updatedComponents.map { it.componentIngredientId } + batch.outputIngredientId).distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_PROJECTIONS)

            // Mark batch POSTED
            batchDao.update(snapshotBatch.copy(
                status = DocumentStatus.POSTED.name,
                postedAt = postedAtMs,
                updatedAt = postedAtMs
            ))

            failureBoundary.trigger(IntegrationFailurePoints.PRODUCTION_POST_AFTER_MARK_POSTED)
        }
    }
}
