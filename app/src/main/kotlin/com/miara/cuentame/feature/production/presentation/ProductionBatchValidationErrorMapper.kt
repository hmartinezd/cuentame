package com.miara.cuentame.feature.production.presentation

import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.presentation.ui.UiMessage

fun ProductionBatchValidationFailure.toUserMessage(): UiMessage {
    val resId = when (this) {
        ProductionBatchValidationFailure.RestaurantMismatch -> R.string.error_no_restaurant
        ProductionBatchValidationFailure.RecipeNotFound -> R.string.error_recipe_not_found
        ProductionBatchValidationFailure.RecipeNotActive -> R.string.error_recipe_not_active
        ProductionBatchValidationFailure.RecipeHasNoYield -> R.string.error_recipe_no_yield
        ProductionBatchValidationFailure.RecipeHasNoComponents -> R.string.error_recipe_no_components
        ProductionBatchValidationFailure.MultiplierMustBePositive -> R.string.error_multiplier_positive
        ProductionBatchValidationFailure.EffectiveTimeInFuture -> R.string.error_future_effective_time
        ProductionBatchValidationFailure.OutputIngredientNotFound -> R.string.error_ingredient_not_found
        ProductionBatchValidationFailure.OutputIngredientInactive -> R.string.error_ingredient_inactive
        ProductionBatchValidationFailure.OutputAreaNotFound -> R.string.error_area_not_found
        ProductionBatchValidationFailure.OutputAreaInactive -> R.string.error_area_inactive
        ProductionBatchValidationFailure.OutputUnitOptionNotFound -> R.string.error_unit_option_not_found
        ProductionBatchValidationFailure.OutputUnitOptionInactive -> R.string.error_unit_inactive
        ProductionBatchValidationFailure.ActualOutputMustBePositive -> R.string.error_quantity_positive
        ProductionBatchValidationFailure.BatchNotFound -> R.string.error_batch_not_found
        ProductionBatchValidationFailure.BatchNotDraft -> R.string.error_batch_not_draft
        ProductionBatchValidationFailure.ComponentNotFound -> R.string.error_component_not_found
        ProductionBatchValidationFailure.ComponentIngredientNotFound -> R.string.error_ingredient_not_found
        ProductionBatchValidationFailure.ComponentIngredientInactive -> R.string.error_ingredient_inactive
        ProductionBatchValidationFailure.ComponentIngredientRestaurantMismatch -> R.string.error_generic
        ProductionBatchValidationFailure.ComponentQuantityMustBePositive -> R.string.error_quantity_positive
        ProductionBatchValidationFailure.InvalidUnitFactor -> R.string.error_generic
        ProductionBatchValidationFailure.SourceAreaNotFound -> R.string.error_area_not_found
        ProductionBatchValidationFailure.SourceAreaInactive -> R.string.error_area_inactive
        ProductionBatchValidationFailure.SourceAreaRestaurantMismatch -> R.string.error_generic
        ProductionBatchValidationFailure.ComponentUnitOptionNotFound -> R.string.error_unit_option_not_found
        ProductionBatchValidationFailure.ComponentUnitOptionInactive -> R.string.error_unit_inactive
        ProductionBatchValidationFailure.ComponentUnitOptionMismatch -> R.string.error_generic
        ProductionBatchValidationFailure.ComponentCostUnavailable -> R.string.error_cost_unavailable
        ProductionBatchValidationFailure.MovementHistoryConflict -> R.string.error_malformed_history
        ProductionBatchValidationFailure.RestrictedByArchive -> R.string.error_restricted_by_archive
    }
    return UiMessage.Resource(resId)
}

fun List<ProductionBatchValidationFailure>.toUserMessage(): UiMessage {
    if (isEmpty()) return UiMessage.Resource(R.string.error_generic)
    
    // Priority mapping
    val prioritized = sortedBy { failure ->
        when (failure) {
            ProductionBatchValidationFailure.BatchNotFound,
            ProductionBatchValidationFailure.RecipeNotFound -> 0
            ProductionBatchValidationFailure.BatchNotDraft -> 1
            ProductionBatchValidationFailure.EffectiveTimeInFuture -> 2
            ProductionBatchValidationFailure.OutputAreaNotFound,
            ProductionBatchValidationFailure.SourceAreaNotFound -> 3
            ProductionBatchValidationFailure.MultiplierMustBePositive,
            ProductionBatchValidationFailure.ActualOutputMustBePositive,
            ProductionBatchValidationFailure.ComponentQuantityMustBePositive -> 4
            ProductionBatchValidationFailure.ComponentCostUnavailable -> 5
            ProductionBatchValidationFailure.MovementHistoryConflict -> 6
            ProductionBatchValidationFailure.RestrictedByArchive -> 7
            else -> 8
        }
    }
    
    return prioritized.first().toUserMessage()
}
