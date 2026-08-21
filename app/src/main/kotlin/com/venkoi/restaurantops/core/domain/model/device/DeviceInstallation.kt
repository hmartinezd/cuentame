package com.venkoi.restaurantops.core.domain.model.device

import com.venkoi.restaurantops.core.common.ids.RestaurantId

data class DeviceInstallation(
    val id: String,
    val restaurantId: RestaurantId,
    val installationId: String
)
