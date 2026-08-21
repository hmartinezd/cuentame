package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation

interface DeviceInstallationRepository {
    suspend fun ensureCurrentDeviceRegistered(
        restaurantId: RestaurantId
    ): Result<DeviceInstallation>
}

open class DeviceInstallationOperationException(
    message: String = "Device installation operation failed"
) : Exception(message)

class DeviceInstallationRevokedException : DeviceInstallationOperationException(
    "Device installation is revoked"
)
