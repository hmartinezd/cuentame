package com.venkoi.restaurantops.feature.preparations.presentation

import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.domain.validation.PreparationRecipeValidationFailure
import com.venkoi.restaurantops.core.domain.validation.PreparationRecipeValidationException
import com.venkoi.restaurantops.core.presentation.ui.UiMessage

fun Throwable.toPreparationRecipeUserMessage(): UiMessage = when (this) {
    is PreparationRecipeValidationException -> failures.toUserMessage()
    else -> UiMessage.Resource(R.string.error_generic)
}

fun List<PreparationRecipeValidationFailure>.toUserMessage(): UiMessage {
    val failure = this.firstOrNull() ?: return UiMessage.Resource(R.string.error_generic)
    
    // Priority handling if multiple failures exist
    val prioritizedFailure = find { it is PreparationRecipeValidationFailure.YieldRequired }
        ?: find { it is PreparationRecipeValidationFailure.AtLeastOneComponentRequired }
        ?: failure

    return prioritizedFailure.toUiMessage()
}

fun PreparationRecipeValidationFailure.toUiMessage(): UiMessage = when (this) {
    PreparationRecipeValidationFailure.RecipeNotFound -> UiMessage.Resource(R.string.error_recipe_not_found)
    PreparationRecipeValidationFailure.RecipeNameRequired -> UiMessage.Resource(R.string.error_recipe_name_required)
    PreparationRecipeValidationFailure.OutputIngredientNotFound -> UiMessage.Resource(R.string.error_select_output_ingredient)
    PreparationRecipeValidationFailure.OutputIngredientDeleted -> UiMessage.Resource(R.string.error_inactive_reference)
    PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant -> UiMessage.Resource(R.string.error_generic)
    PreparationRecipeValidationFailure.OutputUnitOptionMissing -> UiMessage.Resource(R.string.error_yield_pairing_required)
    PreparationRecipeValidationFailure.YieldRequired -> UiMessage.Resource(R.string.missing_yield)
    PreparationRecipeValidationFailure.YieldMustBePositive -> UiMessage.Resource(R.string.error_quantity_positive)
    PreparationRecipeValidationFailure.YieldUnitNotFound -> UiMessage.Resource(R.string.missing_yield)
    PreparationRecipeValidationFailure.YieldUnitInactive -> UiMessage.Resource(R.string.error_inactive_reference)
    PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput -> UiMessage.Resource(R.string.error_generic)
    PreparationRecipeValidationFailure.AtLeastOneComponentRequired -> UiMessage.Resource(R.string.missing_components)
    PreparationRecipeValidationFailure.ComponentIngredientNotFound -> UiMessage.Resource(R.string.error_select_component_ingredient)
    PreparationRecipeValidationFailure.ComponentIngredientDeleted -> UiMessage.Resource(R.string.error_inactive_reference)
    PreparationRecipeValidationFailure.ComponentMustBelongToRestaurant -> UiMessage.Resource(R.string.error_generic)
    PreparationRecipeValidationFailure.ComponentCannotBeOutput -> UiMessage.Resource(R.string.error_output_as_component)
    PreparationRecipeValidationFailure.ComponentAlreadyExists -> UiMessage.Resource(R.string.error_duplicate_component)
    PreparationRecipeValidationFailure.ComponentQuantityMustBePositive -> UiMessage.Resource(R.string.error_quantity_positive)
    PreparationRecipeValidationFailure.ComponentUnitNotFound -> UiMessage.Resource(R.string.error_select_unit)
    PreparationRecipeValidationFailure.ComponentUnitInactive -> UiMessage.Resource(R.string.error_inactive_reference)
    PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient -> UiMessage.Resource(R.string.error_generic)
    PreparationRecipeValidationFailure.ComponentNotFound -> UiMessage.Resource(R.string.error_recipe_component_not_found)
    PreparationRecipeValidationFailure.ComponentDoesNotBelongToRecipe -> UiMessage.Resource(R.string.error_generic)
    PreparationRecipeValidationFailure.InvalidComponentOrder -> UiMessage.Resource(R.string.error_reorder_components_failed)
    PreparationRecipeValidationFailure.RecipeWouldCreateCycle -> UiMessage.Resource(R.string.circular_recipe_warning)
    PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput -> UiMessage.Resource(R.string.error_recipe_exists_for_output)
    PreparationRecipeValidationFailure.InvalidStatusTransition -> UiMessage.Resource(R.string.error_recipe_not_editable)
    PreparationRecipeValidationFailure.UnitOptionUsedByRecipe,
    PreparationRecipeValidationFailure.UnitOptionUsedByRecipeComponent -> UiMessage.Resource(R.string.error_generic)
}
