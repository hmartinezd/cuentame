package com.venkoi.restaurantops.core.cloud.device

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRevokedException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DeviceInstallationDto(
    val id: String,
    @SerialName("restaurant_id")
    val restaurantId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("installation_id")
    val installationId: String,
    val platform: String,
    @SerialName("device_name")
    val deviceName: String? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("revoked_at")
    val revokedAt: String? = null
)

@Serializable
internal data class DeviceInstallationInsertDto(
    @SerialName("restaurant_id")
    val restaurantId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("installation_id")
    val installationId: String,
    val platform: String,
    @SerialName("device_name")
    val deviceName: String,
    @SerialName("app_version")
    val appVersion: String
)

internal fun DeviceInstallationDto.toDomain(): DeviceInstallation = DeviceInstallation(
    id = id,
    restaurantId = RestaurantId(restaurantId),
    installationId = installationId
)

internal fun activeDeviceOrThrow(device: DeviceInstallationDto): DeviceInstallation {
    if (device.revokedAt != null) throw DeviceInstallationRevokedException()
    return device.toDomain()
}

internal fun buildDeviceName(manufacturer: String, model: String): String {
    val normalizedManufacturer = manufacturer.trim().ifBlank { "Unknown" }
    val normalizedModel = model.trim().ifBlank { "Unknown" }
    return if (normalizedModel.startsWith(normalizedManufacturer, ignoreCase = true)) {
        normalizedModel
    } else {
        "$normalizedManufacturer $normalizedModel"
    }
}
