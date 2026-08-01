package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.PreparationRecipeDao
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidationFailure
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidator
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

class RoomPreparationRecipeRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val recipeDao: PreparationRecipeDao,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val validator: PreparationRecipeValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : PreparationRecipeRepository {

    override fun observeRecipes(
        restaurantId: RestaurantId,
        includeArchived: Boolean
    ): Flow<List<PreparationRecipeSummary>> {
        return recipeDao.observeRecipeSummaries(restaurantId.value, includeArchived).map { rows ->
            rows.map { it.toDomain() }
        }
    }

    override fun observeRecipe(recipeId: PreparationRecipeId): Flow<PreparationRecipe?> {
        return recipeDao.observeById(recipeId.value).combine(
            recipeDao.observeComponentsForRecipe(recipeId.value)
        ) { entity, components ->
            entity?.toDomain(components)
        }
    }

    override suspend fun getRecipe(recipeId: PreparationRecipeId): PreparationRecipe? {
        val entity = recipeDao.getById(recipeId.value) ?: return null
        val components = recipeDao.getComponentsForRecipe(entity.id)
        return entity.toDomain(components)
    }

    override suspend fun getRecipeForOutputIngredient(
        restaurantId: RestaurantId,
        outputIngredientId: IngredientId
    ): PreparationRecipe? {
        val entity = recipeDao.getActiveOrDraftByOutputIngredient(restaurantId.value, outputIngredientId.value) ?: return null
        val components = recipeDao.getComponentsForRecipe(entity.id)
        return entity.toDomain(components)
    }

    override suspend fun createDraft(command: CreatePreparationRecipeCommand): PreparationRecipeId {
        return database.withTransaction {
            val outputIngredient = ingredientDao.getById(command.outputIngredientId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.OutputIngredientNotFound))

            if (outputIngredient.deletedAt != null || !outputIngredient.isActive) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.OutputIngredientDeleted))
            }

            if (outputIngredient.restaurantId != command.restaurantId.value) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant))
            }

            // Milestone 1 constraint: at most one non-archived recipe per output ingredient
            val existing = recipeDao.getActiveOrDraftByOutputIngredient(command.restaurantId.value, command.outputIngredientId.value)
            if (existing != null) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput))
            }

            val recipeId = PreparationRecipeId(idGenerator.newId())
            val now = timeProvider.now().toEpochMilli()
            val name = command.name ?: outputIngredient.name

            if (name.isBlank()) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNameRequired))
            }
            
            val unitOption = command.yieldUnitOptionId?.let { unitOptionDao.getById(it.value) }
            if (unitOption != null && (unitOption.deletedAt != null || !unitOption.isActive)) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.YieldUnitInactive))
            }

            val standardYieldQuantityBase = if (command.standardYieldQuantity != null && unitOption != null) {
                command.standardYieldQuantity.multiply(unitOption.factorToBase)
            } else null

            val entity = PreparationRecipeEntity(
                id = recipeId.value,
                restaurantId = command.restaurantId.value,
                outputIngredientId = command.outputIngredientId.value,
                name = name,
                normalizedName = name.normalizeName(),
                standardYieldQuantity = command.standardYieldQuantity,
                standardYieldQuantityBase = standardYieldQuantityBase,
                yieldUnitOptionId = command.yieldUnitOptionId?.value,
                status = PreparationRecipeStatus.DRAFT.name,
                notes = command.notes,
                createdAt = now,
                updatedAt = now,
                archivedAt = null
            )

            val draftFailures = validator.validateDraft(
                recipe = entity.toDomain(emptyList()),
                outputIngredient = outputIngredient.toDomain(),
                yieldUnitOption = unitOption?.toDomain()
            )
            if (draftFailures.isNotEmpty()) {
                throw RecipeValidationException(draftFailures)
            }

            recipeDao.insert(entity)
            recipeId
        }
    }

    override suspend fun updateDraft(command: UpdatePreparationRecipeCommand) {
        database.withTransaction {
            val existing = recipeDao.getById(command.recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (existing.status != PreparationRecipeStatus.DRAFT.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            if (command.name.isBlank()) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNameRequired))
            }

            val outputIngredient = ingredientDao.getById(existing.outputIngredientId)
            val unitOption = command.yieldUnitOptionId?.let { unitOptionDao.getById(it.value) }

            if (unitOption != null && (unitOption.deletedAt != null || !unitOption.isActive)) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.YieldUnitInactive))
            }
            
            val standardYieldQuantityBase = if (command.standardYieldQuantity != null && unitOption != null) {
                command.standardYieldQuantity.multiply(unitOption.factorToBase)
            } else null

            val updated = existing.copy(
                name = command.name,
                normalizedName = command.name.normalizeName(),
                standardYieldQuantity = command.standardYieldQuantity,
                standardYieldQuantityBase = standardYieldQuantityBase,
                yieldUnitOptionId = command.yieldUnitOptionId?.value,
                notes = command.notes,
                updatedAt = timeProvider.now().toEpochMilli()
            )

            val draftFailures = validator.validateDraft(
                recipe = updated.toDomain(emptyList()),
                outputIngredient = outputIngredient?.toDomain(),
                yieldUnitOption = unitOption?.toDomain()
            )
            if (draftFailures.isNotEmpty()) {
                throw RecipeValidationException(draftFailures)
            }

            recipeDao.update(updated)
        }
    }

    override suspend fun saveComponent(command: SavePreparationRecipeComponentCommand): PreparationRecipeComponentId {
        return database.withTransaction {
            val recipe = recipeDao.getById(command.recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.DRAFT.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val ingredient = ingredientDao.getById(command.componentIngredientId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentIngredientNotFound))

            if (ingredient.deletedAt != null || !ingredient.isActive) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentIngredientDeleted))
            }

            if (ingredient.restaurantId != recipe.restaurantId) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentMustBelongToRestaurant))
            }

            if (ingredient.id == recipe.outputIngredientId) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentCannotBeOutput))
            }

            val unitOption = unitOptionDao.getById(command.unitOptionId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentUnitNotFound))

            if (unitOption.deletedAt != null || !unitOption.isActive) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentUnitInactive))
            }

            if (unitOption.ingredientId != command.componentIngredientId.value) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient))
            }

            if (command.quantityEntered.compareTo(BigDecimal.ZERO) <= 0) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive))
            }

            val now = timeProvider.now().toEpochMilli()
            val quantityBase = command.quantityEntered.multiply(unitOption.factorToBase)

            // Collision check: Search for another component using the proposed ingredient
            val duplicate = recipeDao.getComponentByIngredient(recipe.id, command.componentIngredientId.value)
            
            if (command.componentId != null) {
                // Editing an existing component
                val existingById = recipeDao.getComponentById(command.componentId.value)
                    ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentNotFound))
                
                if (existingById.recipeId != recipe.id) {
                    throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentDoesNotBelongToRecipe))
                }

                if (duplicate != null && duplicate.id != command.componentId.value) {
                    throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentAlreadyExists))
                }

                val targetEntity = existingById.copy(
                    componentIngredientId = command.componentIngredientId.value,
                    unitOptionId = command.unitOptionId.value,
                    quantityEntered = command.quantityEntered,
                    quantityBase = quantityBase,
                    sortOrder = command.sortOrder,
                    notes = command.notes,
                    updatedAt = now
                )
                recipeDao.upsertComponent(targetEntity)
                PreparationRecipeComponentId(targetEntity.id)
            } else {
                // Add without component ID
                val targetEntity = if (duplicate != null) {
                    // Update existing component
                    duplicate.copy(
                        unitOptionId = command.unitOptionId.value,
                        quantityEntered = command.quantityEntered,
                        quantityBase = quantityBase,
                        sortOrder = command.sortOrder,
                        notes = command.notes,
                        updatedAt = now
                    )
                } else {
                    // Brand new component
                    PreparationRecipeComponentEntity(
                        id = PreparationRecipeComponentId(idGenerator.newId()).value,
                        recipeId = command.recipeId.value,
                        componentIngredientId = command.componentIngredientId.value,
                        unitOptionId = command.unitOptionId.value,
                        quantityEntered = command.quantityEntered,
                        quantityBase = quantityBase,
                        sortOrder = command.sortOrder,
                        notes = command.notes,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                recipeDao.upsertComponent(targetEntity)
                PreparationRecipeComponentId(targetEntity.id)
            }
        }
    }

    override suspend fun removeComponent(recipeId: PreparationRecipeId, componentId: PreparationRecipeComponentId) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.DRAFT.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val component = recipeDao.getComponentById(componentId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentNotFound))

            if (component.recipeId != recipe.id) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.ComponentDoesNotBelongToRecipe))
            }

            recipeDao.deleteComponent(recipeId.value, componentId.value)
        }
    }

    override suspend fun reorderComponents(
        recipeId: PreparationRecipeId,
        orderedComponentIds: List<PreparationRecipeComponentId>
    ) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.DRAFT.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val currentComponents = recipeDao.getComponentsForRecipe(recipe.id)
            val currentIds = currentComponents.map { it.id }.toSet()
            val orderedIds = orderedComponentIds.map { it.value }.toSet()

            if (orderedIds.size != orderedComponentIds.size || currentIds != orderedIds) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidComponentOrder))
            }

            recipeDao.reorderComponents(
                recipeId.value,
                orderedComponentIds.map { it.value },
                timeProvider.now().toEpochMilli()
            )
        }
    }

    override suspend fun activate(recipeId: PreparationRecipeId) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.DRAFT.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val outputIngredient = ingredientDao.getById(recipe.outputIngredientId)
            val components = recipeDao.getComponentsForRecipe(recipe.id)
            
            val allOutputUnitOptions = unitOptionDao.getByIngredient(recipe.outputIngredientId)
            
            val componentIngredientIds = components.map { it.componentIngredientId }
            val allComponentIngredients = ingredientDao.getByIds(componentIngredientIds).associateBy { it.id }
            val allComponentUnitOptions = unitOptionDao.getByIngredients(componentIngredientIds).groupBy { it.ingredientId }
            
            val existingGraphEdges = recipeDao.getNonArchivedDependencyGraph(recipe.restaurantId)

            val failures = validator.validateActivation(
                recipe = recipe.toDomain(components),
                outputIngredient = outputIngredient?.toDomain(),
                components = components.map { it.toDomain() },
                allOutputUnitOptions = allOutputUnitOptions.map { it.toDomain() },
                allComponentIngredients = allComponentIngredients.mapValues { it.value.toDomain() },
                allComponentUnitOptions = allComponentUnitOptions.mapValues { it.value.map { opt -> opt.toDomain() } },
                existingGraphEdges = existingGraphEdges
            )

            if (failures.isNotEmpty()) {
                throw RecipeValidationException(failures)
            }

            recipeDao.updateStatus(
                recipeId = recipe.id,
                status = PreparationRecipeStatus.ACTIVE.name,
                updatedAt = timeProvider.now().toEpochMilli(),
                archivedAt = null
            )
        }
    }

    override suspend fun moveToDraft(recipeId: PreparationRecipeId) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.ACTIVE.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            recipeDao.updateStatus(
                recipeId = recipeId.value,
                status = PreparationRecipeStatus.DRAFT.name,
                updatedAt = timeProvider.now().toEpochMilli(),
                archivedAt = null
            )
        }
    }

    override suspend fun archive(recipeId: PreparationRecipeId) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status == PreparationRecipeStatus.ARCHIVED.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val now = timeProvider.now().toEpochMilli()
            recipeDao.updateStatus(
                recipeId = recipeId.value,
                status = PreparationRecipeStatus.ARCHIVED.name,
                updatedAt = now,
                archivedAt = now
            )
        }
    }

    override suspend fun restoreToDraft(recipeId: PreparationRecipeId) {
        database.withTransaction {
            val recipe = recipeDao.getById(recipeId.value)
                ?: throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))

            if (recipe.status != PreparationRecipeStatus.ARCHIVED.name) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.InvalidStatusTransition))
            }

            val existing = recipeDao.getActiveOrDraftByOutputIngredient(recipe.restaurantId, recipe.outputIngredientId)
            if (existing != null) {
                throw RecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput))
            }

            recipeDao.updateStatus(
                recipeId = recipeId.value,
                status = PreparationRecipeStatus.DRAFT.name,
                updatedAt = timeProvider.now().toEpochMilli(),
                archivedAt = null
            )
        }
    }
}

class RecipeValidationException(val failures: List<PreparationRecipeValidationFailure>) : Exception(failures.joinToString())
