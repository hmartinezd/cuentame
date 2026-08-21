package com.venkoi.cuentame.feature.production.presentation

import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.domain.repository.PostingBlocker
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionPostingBlockerMapperTest {

    @Test
    fun `maps all blockers to correct strings`() {
        val mapping = mapOf(
            PostingBlocker.RECIPE_NOT_ACTIVE to R.string.error_recipe_not_active,
            PostingBlocker.MISSING_COMPONENT_AREA to R.string.error_missing_component_area,
            PostingBlocker.COMPONENT_COST_UNAVAILABLE to R.string.error_cost_unavailable,
            PostingBlocker.FUTURE_EFFECTIVE_TIME to R.string.error_future_effective_time,
            PostingBlocker.INVALID_RESTAURANT to R.string.error_no_restaurant,
            PostingBlocker.RESTRICTED_BY_ARCHIVE to R.string.error_restricted_by_archive,
            PostingBlocker.OUTPUT_REFERENCE_INVALID to R.string.error_ingredient_not_found,
            PostingBlocker.OUTPUT_UNIT_INVALID to R.string.error_unit_option_not_found,
            PostingBlocker.OUTPUT_AREA_INVALID to R.string.error_area_not_found,
            PostingBlocker.COMPONENT_REFERENCE_INVALID to R.string.error_ingredient_not_found,
            PostingBlocker.COMPONENT_UNIT_INVALID to R.string.error_unit_option_not_found,
            PostingBlocker.COMPONENT_AREA_INVALID to R.string.error_area_not_found,
            PostingBlocker.COMPONENT_QUANTITY_INVALID to R.string.error_quantity_positive
        )

        mapping.forEach { (blocker, expectedRes) ->
            val message = blocker.toUserMessage() as UiMessage.Resource
            assertEquals("Mismatch for $blocker", expectedRes, message.id)
        }
    }
}
