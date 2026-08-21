package com.venkoi.restaurantops.core.model.onboarding

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess

data class CloudRestaurantSetupContext(
    val restaurantId: RestaurantId,
    val restaurantName: String,
    val currencyCode: String,
    val localeTag: String
) {
    companion object {
        fun from(access: RestaurantAccess): CloudRestaurantSetupContext =
            CloudRestaurantSetupContext(
                restaurantId = RestaurantId(access.restaurant.id),
                restaurantName = access.restaurant.name,
                currencyCode = access.restaurant.currencyCode,
                localeTag = access.restaurant.localeTag
            )
    }
}
