package com.venkoi.restaurantops.feature.production.presentation

import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.domain.repository.PostingBlocker
import com.venkoi.restaurantops.core.presentation.ui.UiMessage

fun PostingBlocker.toUserMessage(): UiMessage {
    val resId = when (this) {
        PostingBlocker.RECIPE_NOT_ACTIVE -> R.string.error_recipe_not_active
        PostingBlocker.MISSING_COMPONENT_AREA -> R.string.error_missing_component_area
        PostingBlocker.COMPONENT_COST_UNAVAILABLE -> R.string.error_cost_unavailable
        PostingBlocker.FUTURE_EFFECTIVE_TIME -> R.string.error_future_effective_time
        PostingBlocker.INVALID_RESTAURANT -> R.string.error_no_restaurant
        PostingBlocker.RESTRICTED_BY_ARCHIVE -> R.string.error_restricted_by_archive
        PostingBlocker.OUTPUT_REFERENCE_INVALID -> R.string.error_ingredient_not_found
        PostingBlocker.OUTPUT_UNIT_INVALID -> R.string.error_unit_option_not_found
        PostingBlocker.OUTPUT_AREA_INVALID -> R.string.error_area_not_found
        PostingBlocker.COMPONENT_REFERENCE_INVALID -> R.string.error_ingredient_not_found
        PostingBlocker.COMPONENT_UNIT_INVALID -> R.string.error_unit_option_not_found
        PostingBlocker.COMPONENT_AREA_INVALID -> R.string.error_area_not_found
        PostingBlocker.COMPONENT_QUANTITY_INVALID -> R.string.error_quantity_positive
    }
    return UiMessage.Resource(resId)
}
