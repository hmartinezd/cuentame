package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.ProductionBatchComponentEntity
import com.miara.cuentame.core.database.entity.ProductionBatchEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

class RoomProductionBatchRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val batchDao: ProductionBatchDao,
    private val recipeDao: PreparationRecipeDao,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val areaDao: InventoryAreaDao,
    private val inventorySnapshotService: InventorySnapshotService,
    private val postingCoordinator: ProductionBatchPostingCoordinator,
    private val voidingCoordinator: ProductionBatchVoidingCoordinator,
    private val activeRestaurantProvider: ActiveRestaurantProvider,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : ProductionBatchRepository {

    override fun observeBatches(
        restaurantId: RestaurantId,
        status: DocumentStatus?
    ): Flow<List<ProductionBatchSummary>> {
        return batchDao.observeSummaries(restaurantId.value, status?.name).map { rows ->
            rows.map { it.toDomain() }
        }
    }

    override fun observeBatch(batchId: ProductionBatchId): Flow<ProductionBatch?> {
        return batchDao.observeById(batchId.value).combine(
            batchDao.observeComponents(batchId.value)
        ) { entity, components ->
            entity?.toDomain(components)
        }
    }

    override suspend fun getBatch(batchId: ProductionBatchId): ProductionBatch? {
        val entity = batchDao.getById(batchId.value) ?: return null
        val components = batchDao.getComponents(batchId.value)
        return entity.toDomain(components)
    }

    override suspend fun createDraft(command: CreateProductionBatchDraftCommand): ProductionBatchId {
        return database.withTransaction {
            val recipe = recipeDao.getById(command.recipeId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeNotFound))

            if (recipe.restaurantId != command.restaurantId.value) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RestaurantMismatch))
            }

            if (recipe.status != PreparationRecipeStatus.ACTIVE.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeNotActive))
            }

            val recipeStandardYieldQuantity = recipe.standardYieldQuantity
            val recipeStandardYieldQuantityBase = recipe.standardYieldQuantityBase
            val recipeYieldUnitOptionId = recipe.yieldUnitOptionId

            if (recipeStandardYieldQuantity == null || recipeStandardYieldQuantity.compareTo(BigDecimal.ZERO) <= 0 ||
                recipeStandardYieldQuantityBase == null || recipeStandardYieldQuantityBase.compareTo(BigDecimal.ZERO) <= 0 ||
                recipeYieldUnitOptionId == null
            ) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeHasNoYield))
            }

            val recipeComponents = recipeDao.getComponentsForRecipe(recipe.id)
            if (recipeComponents.isEmpty()) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.RecipeHasNoComponents))
            }

            if (command.batchMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
            }

            val now = timeProvider.now()
            if (command.effectiveAt > now) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.EffectiveTimeInFuture))
            }

            val outputIngredient = ingredientDao.getById(recipe.outputIngredientId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientNotFound))
            if (!outputIngredient.isActive || outputIngredient.deletedAt != null) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputIngredientInactive))
            }

            val outputArea = areaDao.getById(command.outputAreaId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaNotFound))
            if (!outputArea.isActive || outputArea.deletedAt != null || outputArea.restaurantId != command.restaurantId.value) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaInactive))
            }

            val selectedOptionId = command.outputUnitOptionId?.value ?: recipeYieldUnitOptionId
            val outputUnitOption = unitOptionDao.getById(selectedOptionId)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionNotFound))
            if (!outputUnitOption.isActive || outputUnitOption.deletedAt != null || outputUnitOption.ingredientId != recipe.outputIngredientId) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionInactive))
            }

            val expectedOutputQuantityEntered = recipeStandardYieldQuantity.multiply(command.batchMultiplier)
            val expectedOutputQuantityBase = recipeStandardYieldQuantityBase.multiply(command.batchMultiplier)

            val actualOutputQuantityEntered = command.actualOutputQuantityEntered ?: run {
                // Convert expected output base into selected output option
                expectedOutputQuantityBase.divide(outputUnitOption.factorToBase, java.math.MathContext.DECIMAL128)
            }

            if (actualOutputQuantityEntered.compareTo(BigDecimal.ZERO) <= 0) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ActualOutputMustBePositive))
            }

            val actualOutputQuantityBase = actualOutputQuantityEntered.multiply(outputUnitOption.factorToBase)

            val batchId = ProductionBatchId(idGenerator.newId())
            val nowMs = now.toEpochMilli()

            val batchEntity = ProductionBatchEntity(
                id = batchId.value,
                restaurantId = command.restaurantId.value,
                recipeId = recipe.id,
                recipeNameSnapshot = recipe.name,
                outputIngredientId = recipe.outputIngredientId,
                batchMultiplier = command.batchMultiplier.toPlainString(),
                recipeStandardYieldQuantitySnapshot = recipeStandardYieldQuantity.toPlainString(),
                recipeStandardYieldBaseSnapshot = recipeStandardYieldQuantityBase.toPlainString(),
                recipeYieldUnitOptionIdSnapshot = recipeYieldUnitOptionId,
                expectedOutputQuantityEntered = expectedOutputQuantityEntered.toPlainString(),
                expectedOutputQuantityBase = expectedOutputQuantityBase.toPlainString(),
                actualOutputQuantityEntered = actualOutputQuantityEntered.toPlainString(),
                actualOutputQuantityBase = actualOutputQuantityBase.toPlainString(),
                outputUnitOptionId = outputUnitOption.id,
                outputAreaId = outputArea.id,
                hasManualOutputQuantityOverride = command.actualOutputQuantityEntered != null,
                totalComponentCostSnapshot = null,
                outputUnitCostBaseSnapshot = null,
                effectiveAt = command.effectiveAt.toEpochMilli(),
                status = DocumentStatus.DRAFT.name,
                notes = command.notes,
                createdAt = nowMs,
                updatedAt = nowMs,
                postedAt = null,
                voidedAt = null
            )

            val componentEntities = recipeComponents.map { rc ->
                val expectedQuantityEntered = rc.quantityEntered.multiply(command.batchMultiplier)
                val expectedQuantityBase = rc.quantityBase.multiply(command.batchMultiplier)

                val componentIngredient = ingredientDao.getById(rc.componentIngredientId)
                val defaultAreaId = componentIngredient?.defaultAreaId?.let { areaId ->
                    val area = areaDao.getById(areaId)
                    if (area != null && area.isActive && area.deletedAt == null && area.restaurantId == command.restaurantId.value) {
                        area.id
                    } else null
                }

                ProductionBatchComponentEntity(
                    id = idGenerator.newId(),
                    productionBatchId = batchId.value,
                    sourceRecipeComponentIdSnapshot = rc.id,
                    componentIngredientId = rc.componentIngredientId,
                    recipeQuantityEnteredSnapshot = rc.quantityEntered.toPlainString(),
                    recipeQuantityBaseSnapshot = rc.quantityBase.toPlainString(),
                    recipeUnitOptionIdSnapshot = rc.unitOptionId,
                    expectedQuantityEntered = expectedQuantityEntered.toPlainString(),
                    expectedQuantityBase = expectedQuantityBase.toPlainString(),
                    actualQuantityEntered = expectedQuantityEntered.toPlainString(),
                    actualQuantityBase = expectedQuantityBase.toPlainString(),
                    unitOptionId = rc.unitOptionId,
                    hasManualQuantityOverride = false,
                    sourceAreaId = defaultAreaId,
                    unitCostBaseSnapshot = null,
                    totalCostSnapshot = null,
                    sortOrder = rc.sortOrder,
                    notes = null,
                    createdAt = nowMs,
                    updatedAt = nowMs
                )
            }

            batchDao.insert(batchEntity)
            batchDao.insertComponents(componentEntities)
            batchId
        }
    }

    override suspend fun updateDraft(command: UpdateProductionBatchDraftCommand) {
        database.withTransaction {
            val existing = batchDao.getById(command.batchId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))

            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }

            var updated = existing.copy(updatedAt = timeProvider.now().toEpochMilli())

            if (command.notes != null) {
                updated = updated.copy(notes = command.notes)
            }

            if (command.effectiveAt != null) {
                if (command.effectiveAt > timeProvider.now()) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.EffectiveTimeInFuture))
                }
                updated = updated.copy(effectiveAt = command.effectiveAt.toEpochMilli())
            }

            if (command.outputAreaId != null) {
                val area = areaDao.getById(command.outputAreaId.value)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaNotFound))
                if (!area.isActive || area.deletedAt != null || area.restaurantId != existing.restaurantId) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputAreaInactive))
                }
                updated = updated.copy(outputAreaId = area.id)
            }

            val multiplierChanged = command.batchMultiplier != null && command.batchMultiplier.compareTo(BigDecimal(existing.batchMultiplier)) != 0
            if (command.batchMultiplier != null) {
                if (command.batchMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
                }
                updated = updated.copy(batchMultiplier = command.batchMultiplier.toPlainString())
            }

            if (command.actualOutputQuantityEntered != null || command.outputUnitOptionId != null) {
                val selectedOptionId = command.outputUnitOptionId?.value ?: existing.outputUnitOptionId
                val unitOption = unitOptionDao.getById(selectedOptionId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionNotFound))
                if (!unitOption.isActive || unitOption.deletedAt != null || unitOption.ingredientId != existing.outputIngredientId) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.OutputUnitOptionInactive))
                }

                val actualEntered = command.actualOutputQuantityEntered ?: BigDecimal(existing.actualOutputQuantityEntered)
                if (actualEntered.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ActualOutputMustBePositive))
                }

                updated = updated.copy(
                    actualOutputQuantityEntered = actualEntered.toPlainString(),
                    actualOutputQuantityBase = actualEntered.multiply(unitOption.factorToBase).toPlainString(),
                    outputUnitOptionId = unitOption.id,
                    hasManualOutputQuantityOverride = true
                )
            }

            if (multiplierChanged && command.batchMultiplier != null) {
                val newMultiplier = command.batchMultiplier
                val recipeStandardYieldQuantity = BigDecimal(existing.recipeStandardYieldQuantitySnapshot)
                val recipeStandardYieldBase = BigDecimal(existing.recipeStandardYieldBaseSnapshot)

                val newExpectedEntered = recipeStandardYieldQuantity.multiply(newMultiplier)
                val newExpectedBase = recipeStandardYieldBase.multiply(newMultiplier)

                updated = updated.copy(
                    expectedOutputQuantityEntered = newExpectedEntered.toPlainString(),
                    expectedOutputQuantityBase = newExpectedBase.toPlainString()
                )

                if (!updated.hasManualOutputQuantityOverride) {
                    updated = updated.copy(
                        actualOutputQuantityEntered = newExpectedEntered.toPlainString(),
                        actualOutputQuantityBase = newExpectedBase.toPlainString()
                    )
                }

                // Update components
                val components = batchDao.getComponents(existing.id)
                components.forEach { component ->
                    val newCompExpectedEntered = BigDecimal(component.recipeQuantityEnteredSnapshot).multiply(newMultiplier)
                    val newCompExpectedBase = BigDecimal(component.recipeQuantityBaseSnapshot).multiply(newMultiplier)

                    var updatedComponent = component.copy(
                        expectedQuantityEntered = newCompExpectedEntered.toPlainString(),
                        expectedQuantityBase = newCompExpectedBase.toPlainString(),
                        updatedAt = updated.updatedAt
                    )

                    if (!component.hasManualQuantityOverride) {
                        updatedComponent = updatedComponent.copy(
                            actualQuantityEntered = newCompExpectedEntered.toPlainString(),
                            actualQuantityBase = newCompExpectedBase.toPlainString()
                        )
                    }
                    batchDao.updateComponent(updatedComponent)
                }
            }

            batchDao.update(updated)
        }
    }

    override suspend fun updateComponent(command: UpdateProductionBatchComponentCommand) {
        database.withTransaction {
            val batch = batchDao.getById(command.batchId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))

            if (batch.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }

            val component = batchDao.getComponentById(command.componentId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentNotFound))

            if (component.productionBatchId != batch.id) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentNotFound))
            }

            var updated = component.copy(updatedAt = timeProvider.now().toEpochMilli())

            if (command.notes != null) {
                updated = updated.copy(notes = command.notes)
            }

            if (command.sourceAreaId != null) {
                val area = areaDao.getById(command.sourceAreaId.value)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaNotFound))
                if (!area.isActive || area.deletedAt != null || area.restaurantId != batch.restaurantId) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.SourceAreaInactive))
                }
                updated = updated.copy(sourceAreaId = area.id)
            }

            if (command.actualQuantityEntered != null || command.unitOptionId != null) {
                val selectedOptionId = command.unitOptionId?.value ?: component.unitOptionId
                val unitOption = unitOptionDao.getById(selectedOptionId)
                    ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentUnitOptionNotFound))
                if (!unitOption.isActive || unitOption.deletedAt != null || unitOption.ingredientId != component.componentIngredientId) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentUnitOptionInactive))
                }

                val actualEntered = command.actualQuantityEntered ?: BigDecimal(component.actualQuantityEntered)
                if (actualEntered.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ActualOutputMustBePositive)) // Plan says reject zero/negative
                }

                updated = updated.copy(
                    actualQuantityEntered = actualEntered.toPlainString(),
                    actualQuantityBase = actualEntered.multiply(unitOption.factorToBase).toPlainString(),
                    unitOptionId = unitOption.id,
                    hasManualQuantityOverride = true
                )
            }

            batchDao.updateComponent(updated)
        }
    }

    override suspend fun resetComponentToExpected(
        batchId: ProductionBatchId,
        componentId: ProductionBatchComponentId
    ) {
        database.withTransaction {
            val batch = batchDao.getById(batchId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))
            if (batch.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }
            val component = batchDao.getComponentById(componentId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentNotFound))
            if (component.productionBatchId != batch.id) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentNotFound))
            }

            val updated = component.copy(
                actualQuantityEntered = component.expectedQuantityEntered,
                actualQuantityBase = component.expectedQuantityBase,
                unitOptionId = component.recipeUnitOptionIdSnapshot,
                hasManualQuantityOverride = false,
                updatedAt = timeProvider.now().toEpochMilli()
            )
            batchDao.updateComponent(updated)
        }
    }

    override suspend fun calculatePostingPreview(batchId: ProductionBatchId): ProductionBatchPostingPreview {
        val batch = getBatch(batchId)
            ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))

        val blockers = mutableListOf<PostingBlocker>()
        
        val recipe = recipeDao.getById(batch.recipeId.value)
        if (recipe?.status != PreparationRecipeStatus.ACTIVE.name) {
            blockers.add(PostingBlocker.RECIPE_NOT_ACTIVE)
        }

        if (batch.effectiveAt > timeProvider.now()) {
            blockers.add(PostingBlocker.FUTURE_EFFECTIVE_TIME)
        }

        var totalComponentCost = BigDecimal.ZERO
        var costUnavailable = false

        val componentPreviews = batch.components.map { component ->
            val sourceAreaId = component.sourceAreaId
            if (sourceAreaId == null) {
                blockers.add(PostingBlocker.MISSING_COMPONENT_AREA)
            }

            val snapshot = if (sourceAreaId != null) {
                inventorySnapshotService.calculateAt(
                    batch.restaurantId,
                    component.componentIngredientId,
                    sourceAreaId,
                    batch.effectiveAt
                )
            } else null

            val currentBalance = snapshot?.areaQuantityBase ?: BigDecimal.ZERO
            val remainingBalance = currentBalance.subtract(component.actualQuantityBase)
            
            val unitCost = snapshot?.ingredientAverageCostBase
            val totalCost = if (unitCost != null) {
                component.actualQuantityBase.multiply(unitCost)
            } else null

            if (unitCost == null) {
                costUnavailable = true
            } else {
                totalComponentCost = totalComponentCost.add(totalCost!!)
            }

            val ingredient = ingredientDao.getById(component.componentIngredientId.value)
            val sourceArea = sourceAreaId?.let { areaDao.getById(it.value) }
            val unitOption = unitOptionDao.getById(component.unitOptionId.value)

            ProductionBatchComponentPostingPreview(
                componentId = component.id,
                ingredientId = component.componentIngredientId,
                ingredientName = ingredient?.name ?: "",
                sourceAreaId = sourceAreaId ?: InventoryAreaId(""),
                sourceAreaName = sourceArea?.name ?: "",
                actualQuantityEntered = component.actualQuantityEntered,
                actualQuantityBase = component.actualQuantityBase,
                unitOptionLabel = unitOption?.displayName ?: "",
                currentAreaBalanceBase = currentBalance,
                remainingAreaBalanceBase = remainingBalance,
                createsNegativeBalance = remainingBalance.compareTo(BigDecimal.ZERO) < 0,
                averageUnitCostBase = unitCost,
                totalCost = totalCost,
                costUnavailable = unitCost == null
            )
        }

        if (costUnavailable) {
            blockers.add(PostingBlocker.COMPONENT_COST_UNAVAILABLE)
        }

        val yieldVariancePercent = if (batch.expectedOutputQuantityBase.compareTo(BigDecimal.ZERO) > 0) {
            val diff = batch.actualOutputQuantityBase.subtract(batch.expectedOutputQuantityBase)
            diff.divide(batch.expectedOutputQuantityBase, java.math.MathContext.DECIMAL128).multiply(BigDecimal("100"))
        } else null

        val outputUnitCostBase = if (!costUnavailable && batch.actualOutputQuantityBase.compareTo(BigDecimal.ZERO) > 0) {
            totalComponentCost.divide(batch.actualOutputQuantityBase, java.math.MathContext.DECIMAL128)
        } else null

        return ProductionBatchPostingPreview(
            batchId = batch.id,
            effectiveAt = batch.effectiveAt,
            components = componentPreviews,
            totalComponentCost = if (costUnavailable) null else totalComponentCost,
            actualOutputQuantityBase = batch.actualOutputQuantityBase,
            outputUnitCostBase = outputUnitCostBase,
            yieldVariancePercent = yieldVariancePercent,
            blockers = blockers
        )
    }

    override suspend fun deleteDraft(batchId: ProductionBatchId) {
        database.withTransaction {
            val existing = batchDao.getById(batchId.value)
                ?: throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotFound))
            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.BatchNotDraft))
            }
            batchDao.deleteComponentsForBatch(batchId.value)
            batchDao.delete(existing)
        }
    }

    override suspend fun post(batchId: ProductionBatchId) {
        val restaurantId = activeRestaurantProvider.getRequiredActiveRestaurantId()
        postingCoordinator.post(batchId, restaurantId)
    }

    override suspend fun void(batchId: ProductionBatchId) {
        val restaurantId = activeRestaurantProvider.getRequiredActiveRestaurantId()
        voidingCoordinator.void(batchId, restaurantId)
    }
}
