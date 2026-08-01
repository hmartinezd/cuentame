package com.miara.cuentame.core.domain.validation

import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeComponent
import com.miara.cuentame.core.model.ingredient.PreparationRecipeDependencyEdge
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
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
    data object YieldUnitInactive : PreparationRecipeValidationFailure
    data object YieldUnitDoesNotBelongToOutput : PreparationRecipeValidationFailure
    data object AtLeastOneComponentRequired : PreparationRecipeValidationFailure
    data object ComponentIngredientNotFound : PreparationRecipeValidationFailure
    data object ComponentIngredientDeleted : PreparationRecipeValidationFailure
    data object ComponentMustBelongToRestaurant : PreparationRecipeValidationFailure
    data object ComponentCannotBeOutput : PreparationRecipeValidationFailure
    data object ComponentAlreadyExists : PreparationRecipeValidationFailure
    data object ComponentQuantityMustBePositive : PreparationRecipeValidationFailure
    data object ComponentUnitNotFound : PreparationRecipeValidationFailure
    data object ComponentUnitInactive : PreparationRecipeValidationFailure
    data object ComponentUnitDoesNotBelongToIngredient : PreparationRecipeValidationFailure
    data object RecipeWouldCreateCycle : PreparationRecipeValidationFailure
    data object RecipeAlreadyExistsForOutput : PreparationRecipeValidationFailure
    data object InvalidStatusTransition : PreparationRecipeValidationFailure
    data object OutputIngredientMustBelongToRestaurant : PreparationRecipeValidationFailure
    data object ComponentNotFound : PreparationRecipeValidationFailure
    data object ComponentDoesNotBelongToRecipe : PreparationRecipeValidationFailure
    data object InvalidComponentOrder : PreparationRecipeValidationFailure
    data object UnitOptionUsedByRecipe : PreparationRecipeValidationFailure
    data object UnitOptionUsedByRecipeComponent : PreparationRecipeValidationFailure
    data object RecipeNameRequired : PreparationRecipeValidationFailure
}

class PreparationRecipeValidator @Inject constructor(
    private val graphValidator: PreparationRecipeGraphValidator
) {

    fun validateDraft(
        recipe: PreparationRecipe,
        outputIngredient: Ingredient?,
        yieldUnitOption: IngredientUnitOption?
    ): List<PreparationRecipeValidationFailure> {
        val failures = mutableListOf<PreparationRecipeValidationFailure>()

        if (recipe.name.isBlank()) {
            failures.add(PreparationRecipeValidationFailure.RecipeNameRequired)
        }

        if (outputIngredient == null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientNotFound)
        } else if (outputIngredient.deletedAt != null || !outputIngredient.isActive) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientDeleted)
        }

        if (recipe.standardYieldQuantity != null && recipe.standardYieldQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
        }

        if (recipe.yieldUnitOptionId != null && yieldUnitOption == null) {
            failures.add(PreparationRecipeValidationFailure.YieldUnitNotFound)
        } else if (yieldUnitOption != null) {
            if (yieldUnitOption.ingredientId != recipe.outputIngredientId) {
                failures.add(PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput)
            }
            if (yieldUnitOption.deletedAt != null || !yieldUnitOption.isActive) {
                failures.add(PreparationRecipeValidationFailure.YieldUnitInactive)
            }
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

        if (recipe.name.isBlank()) {
            failures.add(PreparationRecipeValidationFailure.RecipeNameRequired)
        }

        // Output validation
        if (outputIngredient == null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientNotFound)
        } else {
            if (outputIngredient.deletedAt != null || !outputIngredient.isActive) {
                failures.add(PreparationRecipeValidationFailure.OutputIngredientDeleted)
            }
            if (outputIngredient.restaurantId != recipe.restaurantId) {
                failures.add(PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant)
            }
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
            } else {
                if (unitOption.ingredientId != recipe.outputIngredientId) {
                    failures.add(PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput)
                }
                if (unitOption.deletedAt != null || !unitOption.isActive) {
                    failures.add(PreparationRecipeValidationFailure.YieldUnitInactive)
                }

                // Base quantity validation
                if (recipe.standardYieldQuantity != null && recipe.standardYieldQuantityBase != null) {
                    val expectedBase = recipe.standardYieldQuantity.multiply(unitOption.factorToBase)
                    if (recipe.standardYieldQuantityBase.compareTo(expectedBase) != 0) {
                        failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
                    }
                } else if (recipe.standardYieldQuantity != null) {
                    failures.add(PreparationRecipeValidationFailure.YieldRequired)
                }
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
                if (ingredient.deletedAt != null || !ingredient.isActive) {
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
            } else {
                if (option.ingredientId != component.componentIngredientId) {
                    failures.add(PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient)
                }
                if (option.deletedAt != null || !option.isActive) {
                    failures.add(PreparationRecipeValidationFailure.ComponentUnitInactive)
                }

                // Base quantity validation
                val expectedBase = component.quantityEntered.multiply(option.factorToBase)
                if (component.quantityBase.compareTo(expectedBase) != 0) {
                    failures.add(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive)
                }
            }

            if (component.quantityEntered.compareTo(BigDecimal.ZERO) <= 0 ||
                component.quantityBase.compareTo(BigDecimal.ZERO) <= 0) {
                failures.add(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive)
            }
        }

        // Cycle detection
        if (failures.isEmpty()) {
            val proposedEdges = components.map { component ->
                PreparationRecipeDependencyEdge(
                    fromId = recipe.outputIngredientId.value,
                    toId = component.componentIngredientId.value
                )
            }

            if (graphValidator.wouldCreateCycle(
                    existingGraphEdges,
                    recipe.outputIngredientId.value,
                    proposedEdges
                )
            ) {
                failures.add(PreparationRecipeValidationFailure.RecipeWouldCreateCycle)
            }
        }

        return failures
    }

    fun validateRestoreToDraft(
        recipe: PreparationRecipe,
        outputIngredient: Ingredient?,
        yieldUnitOption: IngredientUnitOption?,
        components: List<PreparationRecipeComponent>,
        allComponentIngredients: Map<String, Ingredient>,
        allComponentUnitOptions: Map<String, List<IngredientUnitOption>>,
        existingGraphEdges: List<PreparationRecipeDependencyEdge>
    ): List<PreparationRecipeValidationFailure> {
        val failures = mutableListOf<PreparationRecipeValidationFailure>()

        // 1. Basic recipe identity
        if (recipe.name.isBlank()) {
            failures.add(PreparationRecipeValidationFailure.RecipeNameRequired)
        }

        // 2. Output ingredient
        if (outputIngredient == null) {
            failures.add(PreparationRecipeValidationFailure.OutputIngredientNotFound)
        } else {
            if (outputIngredient.deletedAt != null || !outputIngredient.isActive) {
                failures.add(PreparationRecipeValidationFailure.OutputIngredientDeleted)
            }
            if (outputIngredient.restaurantId != recipe.restaurantId) {
                failures.add(PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant)
            }
        }

        // 3. Yield
        if (recipe.standardYieldQuantity != null && recipe.standardYieldQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
        }

        if (recipe.yieldUnitOptionId != null) {
            if (yieldUnitOption == null) {
                failures.add(PreparationRecipeValidationFailure.YieldUnitNotFound)
            } else {
                if (yieldUnitOption.ingredientId != recipe.outputIngredientId) {
                    failures.add(PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput)
                }
                if (yieldUnitOption.deletedAt != null || !yieldUnitOption.isActive) {
                    failures.add(PreparationRecipeValidationFailure.YieldUnitInactive)
                }
                if (recipe.standardYieldQuantity != null && recipe.standardYieldQuantityBase != null) {
                    val expectedBase = recipe.standardYieldQuantity.multiply(yieldUnitOption.factorToBase)
                    if (recipe.standardYieldQuantityBase.compareTo(expectedBase) != 0) {
                        failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
                    }
                }
                if (recipe.standardYieldQuantityBase != null && recipe.standardYieldQuantityBase.compareTo(BigDecimal.ZERO) <= 0) {
                    failures.add(PreparationRecipeValidationFailure.YieldMustBePositive)
                }
            }
        }

        // 4. Components
        val seenIngredients = mutableSetOf<String>()
        components.forEach { component ->
            if (!seenIngredients.add(component.componentIngredientId.value)) {
                failures.add(PreparationRecipeValidationFailure.ComponentAlreadyExists)
            }

            val ingredient = allComponentIngredients[component.componentIngredientId.value]
            if (ingredient == null) {
                failures.add(PreparationRecipeValidationFailure.ComponentIngredientNotFound)
            } else {
                if (ingredient.deletedAt != null || !ingredient.isActive) {
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
            } else {
                if (option.ingredientId != component.componentIngredientId) {
                    failures.add(PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient)
                }
                if (option.deletedAt != null || !option.isActive) {
                    failures.add(PreparationRecipeValidationFailure.ComponentUnitInactive)
                }

                // Base quantity validation
                val expectedBase = component.quantityEntered.multiply(option.factorToBase)
                if (component.quantityBase.compareTo(expectedBase) != 0) {
                    failures.add(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive)
                }
            }

            if (component.quantityEntered.compareTo(BigDecimal.ZERO) <= 0 ||
                component.quantityBase.compareTo(BigDecimal.ZERO) <= 0) {
                failures.add(PreparationRecipeValidationFailure.ComponentQuantityMustBePositive)
            }
        }

        // 5. Dependency graph
        if (failures.isEmpty()) {
            val proposedEdges = components.map { component ->
                PreparationRecipeDependencyEdge(
                    fromId = recipe.outputIngredientId.value,
                    toId = component.componentIngredientId.value
                )
            }

            if (graphValidator.wouldCreateCycle(
                    existingGraphEdges,
                    recipe.outputIngredientId.value,
                    proposedEdges
                )
            ) {
                failures.add(PreparationRecipeValidationFailure.RecipeWouldCreateCycle)
            }
        }

        return failures
    }
}
