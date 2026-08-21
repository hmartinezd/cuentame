package com.venkoi.restaurantops.feature.preparations.presentation

import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.domain.validation.PreparationRecipeValidationFailure
import com.venkoi.restaurantops.core.domain.validation.PreparationRecipeValidationException
import com.venkoi.restaurantops.core.presentation.ui.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparationRecipeValidationErrorMapperTest {

    @Test
    fun `test all validation failure enum values`() {
        val cases = listOf(
            PreparationRecipeValidationFailure.RecipeNotFound to R.string.error_recipe_not_found,
            PreparationRecipeValidationFailure.RecipeNameRequired to R.string.error_recipe_name_required,
            PreparationRecipeValidationFailure.OutputIngredientNotFound to R.string.error_select_output_ingredient,
            PreparationRecipeValidationFailure.OutputIngredientDeleted to R.string.error_inactive_reference,
            PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant to R.string.error_generic,
            PreparationRecipeValidationFailure.OutputUnitOptionMissing to R.string.error_yield_pairing_required,
            PreparationRecipeValidationFailure.YieldRequired to R.string.missing_yield,
            PreparationRecipeValidationFailure.YieldMustBePositive to R.string.error_quantity_positive,
            PreparationRecipeValidationFailure.YieldUnitNotFound to R.string.missing_yield,
            PreparationRecipeValidationFailure.YieldUnitInactive to R.string.error_inactive_reference,
            PreparationRecipeValidationFailure.YieldUnitDoesNotBelongToOutput to R.string.error_generic,
            PreparationRecipeValidationFailure.AtLeastOneComponentRequired to R.string.missing_components,
            PreparationRecipeValidationFailure.ComponentIngredientNotFound to R.string.error_select_component_ingredient,
            PreparationRecipeValidationFailure.ComponentIngredientDeleted to R.string.error_inactive_reference,
            PreparationRecipeValidationFailure.ComponentMustBelongToRestaurant to R.string.error_generic,
            PreparationRecipeValidationFailure.ComponentCannotBeOutput to R.string.error_output_as_component,
            PreparationRecipeValidationFailure.ComponentAlreadyExists to R.string.error_duplicate_component,
            PreparationRecipeValidationFailure.ComponentQuantityMustBePositive to R.string.error_quantity_positive,
            PreparationRecipeValidationFailure.ComponentUnitNotFound to R.string.error_select_unit,
            PreparationRecipeValidationFailure.ComponentUnitInactive to R.string.error_inactive_reference,
            PreparationRecipeValidationFailure.ComponentUnitDoesNotBelongToIngredient to R.string.error_generic,
            PreparationRecipeValidationFailure.ComponentNotFound to R.string.error_recipe_component_not_found,
            PreparationRecipeValidationFailure.ComponentDoesNotBelongToRecipe to R.string.error_generic,
            PreparationRecipeValidationFailure.InvalidComponentOrder to R.string.error_reorder_components_failed,
            PreparationRecipeValidationFailure.RecipeWouldCreateCycle to R.string.circular_recipe_warning,
            PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput to R.string.error_recipe_exists_for_output,
            PreparationRecipeValidationFailure.InvalidStatusTransition to R.string.error_recipe_not_editable,
            PreparationRecipeValidationFailure.UnitOptionUsedByRecipe to R.string.error_generic,
            PreparationRecipeValidationFailure.UnitOptionUsedByRecipeComponent to R.string.error_generic
        )

        cases.forEach { (failure, expectedResId) ->
            val message = failure.toUiMessage()
            assert(message is UiMessage.Resource)
            assertEquals(expectedResId, (message as UiMessage.Resource).id)
        }
    }

    @Test
    fun `empty failure list returns error_generic`() {
        val message = emptyList<PreparationRecipeValidationFailure>().toUserMessage()
        assert(message is UiMessage.Resource)
        assertEquals(R.string.error_generic, (message as UiMessage.Resource).id)
    }

    @Test
    fun `multiple failures with YieldRequired prioritized`() {
        val failures = listOf(
            PreparationRecipeValidationFailure.AtLeastOneComponentRequired,
            PreparationRecipeValidationFailure.YieldRequired
        )
        val message = failures.toUserMessage()
        assert(message is UiMessage.Resource)
        assertEquals(R.string.missing_yield, (message as UiMessage.Resource).id)
    }

    @Test
    fun `multiple failures with missing components prioritized over others but below YieldRequired`() {
        val failures = listOf(
            PreparationRecipeValidationFailure.RecipeNameRequired,
            PreparationRecipeValidationFailure.AtLeastOneComponentRequired
        )
        val message = failures.toUserMessage()
        assert(message is UiMessage.Resource)
        assertEquals(R.string.missing_components, (message as UiMessage.Resource).id)
    }

    @Test
    fun `unknown throwable returns error_generic`() {
        val message = RuntimeException("Boom").toPreparationRecipeUserMessage()
        assert(message is UiMessage.Resource)
        assertEquals(R.string.error_generic, (message as UiMessage.Resource).id)
    }

    @Test
    fun `PreparationRecipeValidationException maps to localized message`() {
        val exception = PreparationRecipeValidationException(listOf(PreparationRecipeValidationFailure.RecipeNotFound))
        val message = exception.toPreparationRecipeUserMessage()
        assert(message is UiMessage.Resource)
        assertEquals(R.string.error_recipe_not_found, (message as UiMessage.Resource).id)
    }
}
