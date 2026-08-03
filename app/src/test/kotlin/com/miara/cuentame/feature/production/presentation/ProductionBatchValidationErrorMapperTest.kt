package com.miara.cuentame.feature.production.presentation

import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.presentation.ui.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionBatchValidationErrorMapperTest {

    @Test
    fun `maps all failures to correct strings`() {
        val mapping = mapOf(
            ProductionBatchValidationFailure.RestaurantMismatch to R.string.error_no_restaurant,
            ProductionBatchValidationFailure.RecipeNotFound to R.string.error_recipe_not_found,
            ProductionBatchValidationFailure.RecipeNotActive to R.string.error_recipe_not_active,
            ProductionBatchValidationFailure.RecipeHasNoYield to R.string.error_recipe_no_yield,
            ProductionBatchValidationFailure.RecipeHasNoComponents to R.string.error_recipe_no_components,
            ProductionBatchValidationFailure.MultiplierMustBePositive to R.string.error_multiplier_positive,
            ProductionBatchValidationFailure.EffectiveTimeInFuture to R.string.error_future_effective_time,
            ProductionBatchValidationFailure.OutputIngredientNotFound to R.string.error_ingredient_not_found,
            ProductionBatchValidationFailure.OutputIngredientInactive to R.string.error_ingredient_inactive,
            ProductionBatchValidationFailure.OutputAreaNotFound to R.string.error_area_not_found,
            ProductionBatchValidationFailure.OutputAreaInactive to R.string.error_area_inactive,
            ProductionBatchValidationFailure.OutputUnitOptionNotFound to R.string.error_unit_option_not_found,
            ProductionBatchValidationFailure.OutputUnitOptionInactive to R.string.error_unit_inactive,
            ProductionBatchValidationFailure.ActualOutputMustBePositive to R.string.error_quantity_positive,
            ProductionBatchValidationFailure.BatchNotFound to R.string.error_batch_not_found,
            ProductionBatchValidationFailure.BatchNotDraft to R.string.error_batch_not_draft,
            ProductionBatchValidationFailure.ComponentNotFound to R.string.error_component_not_found,
            ProductionBatchValidationFailure.ComponentIngredientNotFound to R.string.error_ingredient_not_found,
            ProductionBatchValidationFailure.ComponentIngredientInactive to R.string.error_ingredient_inactive,
            ProductionBatchValidationFailure.ComponentIngredientRestaurantMismatch to R.string.error_no_restaurant,
            ProductionBatchValidationFailure.ComponentQuantityMustBePositive to R.string.error_quantity_positive,
            ProductionBatchValidationFailure.InvalidUnitFactor to R.string.error_generic,
            ProductionBatchValidationFailure.SourceAreaNotFound to R.string.error_area_not_found,
            ProductionBatchValidationFailure.SourceAreaInactive to R.string.error_area_inactive,
            ProductionBatchValidationFailure.SourceAreaRestaurantMismatch to R.string.error_no_restaurant,
            ProductionBatchValidationFailure.ComponentUnitOptionNotFound to R.string.error_unit_option_not_found,
            ProductionBatchValidationFailure.ComponentUnitOptionInactive to R.string.error_unit_inactive,
            ProductionBatchValidationFailure.ComponentUnitOptionMismatch to R.string.error_unit_option_not_found,
            ProductionBatchValidationFailure.ComponentCostUnavailable to R.string.error_cost_unavailable,
            ProductionBatchValidationFailure.MovementHistoryConflict to R.string.error_malformed_history,
            ProductionBatchValidationFailure.RestrictedByArchive to R.string.error_restricted_by_archive
        )

        mapping.forEach { (failure, expectedRes) ->
            val message = failure.toUserMessage() as UiMessage.Resource
            assertEquals("Mismatch for $failure", expectedRes, message.id)
        }
    }

    @Test
    fun `priority mapping - batch not found wins`() {
        val failures = listOf(
            ProductionBatchValidationFailure.MultiplierMustBePositive,
            ProductionBatchValidationFailure.BatchNotFound,
            ProductionBatchValidationFailure.EffectiveTimeInFuture
        )
        val message = failures.toUserMessage() as UiMessage.Resource
        assertEquals(R.string.error_batch_not_found, message.id)
    }

    @Test
    fun `priority mapping - restricted by archive wins over generic`() {
        val failures = listOf(
            ProductionBatchValidationFailure.RestrictedByArchive,
            ProductionBatchValidationFailure.RestaurantMismatch
        )
        val message = failures.toUserMessage() as UiMessage.Resource
        assertEquals(R.string.error_restricted_by_archive, message.id)
    }
}
