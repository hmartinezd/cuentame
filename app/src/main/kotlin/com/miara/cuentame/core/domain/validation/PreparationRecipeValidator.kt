package com.miara.cuentame.core.domain.validation

import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeComponent
import com.miara.cuentame.core.model.ingredient.PreparationRecipeDependencyEdge
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PreparationRecipeValidationFailure {
    data object RecipeNotFound : PreparationRecipeValidationFailure
    data object OutputIngredientNotFound : PreparationRecipeValidationFailure
    data object OutputIngredientDeleted : PreparationRecipeValidationFailure
    data object OutputUnitOptionMissing : PreparationRecipeValidationFailure
    data object YieldRequired : PreparationRecipeValidationFailure
    data object YieldMustBePositive : PreparationRecipeValidationFailure
    data object YieldUnitNotFound : PreparationRecipeValidationFailure
    data object YieldUnitDoesNotBelongToOutput : PreparationRecipeValidationFailure
    data object AtLeastOneComponentRequired : PreparationRecipeValidationFailure
    data object ComponentIngredientNotFound : PreparationRecipeValidationFailure
    data object ComponentIngredientDeleted : PreparationRecipeValidationFailure
    data object ComponentMustBelongToRestaurant : PreparationRecipeValidationFailure
    data object ComponentCannotBeOutput : PreparationRecipeValidationFailure
    data object ComponentAlreadyExists : PreparationRecipeValidationFailure
    data object ComponentQuantityMustBePositive : PreparationRecipeValidationFailure
    data object ComponentUnitNotFound : PreparationRecipeValidationFailure
    data object ComponentUnitDoesNotBelongToIngredient : PreparationRecipeValidationFailure
    data object RecipeWouldCreateCycle : PreparationRecipeValidationFailure
    data object RecipeAlreadyExistsForOutput : PreparationRecipeValidationFailure
    data object InvalidStatusTransition : PreparationRecipeValidationFailure
}

class PreparationRecipeValidator @Inject constructor() {

    fun validateDraft(
        recipe: PreparationRecipe,
        outputIngredient: Ingredient?,
        yieldUnitOption: IngredientUnitOption?
    ): List<PreparationRecipeValidationFailure> {
        val failures = mutableListOf<PreparationRecipeValidationFailure>()

        if (outputIngredient == null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientNotFound)
        } else if (outputIngredient.deletedAt != null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientDeleted)
        }

        if (recipe.yieldUnitOptionId != null && yieldUnitOption == null) {
            failures.add(PreparationRecipeValidationFailure.YieldUnitNotFound)
        } else if (yieldUnitOption != null && yieldUnitOption.ingredientId != recipe.outputIngredientId) {
            failures.add(PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput)
        }

        return failures
    }

    fun validateActivation(
        recipe: PreparationRecipe,
        outputIngredient: Ingredient?,
        components: List<PreparationRecipeComponent>,
        allOutputUnitOptions: List<IngredientUnitOption>,
        allComponentIngredients: Map<String, Ingredient>,
        allComponentUnitOptions: Map<String, List<IngredientUnitOption>>,
        existingGraphEdges: List<PreparationRecipeDependencyEdge>
    ): List<PreparationRecipeValidationFailure> {
        val failures = mutableListOf<PreparationRecipeValidationFailure>()

        // Output validation
        if (outputIngredient == null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientNotFound)
        } else if (outputIngredient.deletedAt != null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientDeleted)
        }

        if (allOutputUnitOptions.isEmpty()) {
            failures.add(PreparationRecipeValidationFailure.OutputUnitOptionMissing)
        }

        // Yield validation
        if (recipe.standardYieldQuantity == null) {
            failures.add(PreparationRecipeValidationFailure.YieldRequired)
        } else if (recipe.standardYieldQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
        }

        if (recipe.yieldUnitOptionId == null) {
            failures.add(PreparationRecipeValidationFailure.YieldUnitNotFound)
        } else {
            val unitOption = allOutputUnitOptions.find { it.id == recipe.yieldUnitOptionId }
            if (unitOption == null) {
                failures.add(PreparationRecipeValidationFailure.YieldUnitNotFound)
            } else if (unitOption.ingredientId != recipe.outputIngredientId) {
                failures.add(PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput)
            }
        }

        // Components validation
        if (components.isEmpty()) {
            failures.add(PreparationRecipeValidationFailure.AtLeastOneComponentRequired)
        }

        components.forEach { component ->
            val ingredient = allComponentIngredients[component.componentIngredientId.value]
            if (ingredient == null) {
                failures.add(PreparationRecipeValidationFailure.ComponentIngredientNotFound)
            } else {
                if (ingredient.deletedAt != null) {
                    failures.add(PreparationRecipeValidationFailure.ComponentIngredientDeleted)
                }
                if (ingredient.restaurantId != recipe.restaurantId) {
                    failures.add(PreparationRecipeValidationFailure.ComponentMustBelongToRestaurant)
                }
                if (ingredient.id == recipe.outputIngredientId) {
                    failures.add(PreparationRecipeValidationFailure.ComponentCannotBeOutput)
                }
            }

            val options = allComponentUnitOptions[component.componentIngredientId.value] ?: emptyList()
            val option = options.find { it.id == component.unitOptionId }
            if (option == null) {
                failures.add(PreparationRecipeValidationFailure.ComponentUnitNotFound)
            } else if (option.ingredientId != component.componentIngredientId) {
                failures.add(PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient)
            }

            if (component.quantityEntered.compareTo(BigDecimal.ZERO) <= 0) {
                failures.add(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive)
            }
        }

        // Cycle detection
        if (failures.isEmpty()) {
            val graph = existingGraphEdges.toMutableList()
            
            // Remove existing version of this recipe from graph if it's already there
            graph.removeAll { it.fromId == recipe.outputIngredientId.value }
            
            // Add proposed components
            components.forEach { component ->
                graph.add(PreparationRecipeDependencyEdge(fromId = recipe.outputIngredientId.value, toId = component.componentIngredientId.value))
            }
            
            if (hasCycle(graph)) {
                failures.add(PreparationRecipeValidationFailure.RecipeWouldCreateCycle)
            }
        }

        return failures
    }

    private fun hasCycle(edges: List<PreparationRecipeDependencyEdge>): Boolean {
        val adjacency = mutableMapOf<String, MutableList<String>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
        }

        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()

        fun isCyclicUtil(node: String): Boolean {
            if (recStack.contains(node)) return true
            if (visited.contains(node)) return false

            visited.add(node)
            recStack.add(node)

            val children = adjacency[node] ?: emptyList()
            for (child in children) {
                if (isCyclicUtil(child)) return true
            }

            recStack.remove(node)
            return false
        }

        for (node in adjacency.keys) {
            if (isCyclicUtil(node)) return true
        }

        return false
    }
}
