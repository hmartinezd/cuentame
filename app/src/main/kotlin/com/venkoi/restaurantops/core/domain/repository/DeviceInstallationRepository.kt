package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation

interface DeviceInstallationRepository {
    suspend fun ensureCurrentDeviceRegistered(
        restaurantId: RestaurantId
    ): Result<DeviceInstallation>
}

class DeviceInstallationOperationException : Exception("Device installation operation failed")
