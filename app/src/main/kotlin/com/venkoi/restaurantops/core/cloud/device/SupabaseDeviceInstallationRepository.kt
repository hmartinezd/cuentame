package com.venkoi.restaurantops.core.cloud.device

import com.venkoi.restaurantops.core.common.AppVersionProvider
import com.venkoi.restaurantops.core.common.DeviceInfoProvider
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationOperationException
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRepository
import com.venkoi.restaurantops.core.domain.service.InstallationIdProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SupabaseDeviceInstallationRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val installationIdProvider: InstallationIdProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val appVersionProvider: AppVersionProvider
) : DeviceInstallationRepository {

    override suspend fun ensureCurrentDeviceRegistered(
        restaurantId: RestaurantId
    ): Result<DeviceInstallation> = deviceInstallationOperation {
        val user = requireNotNull(supabase.auth.currentUserOrNull()) {
            "An authenticated user is required"
        }
        val installationId = installationIdProvider.getOrCreateInstallationId()

        val existing = supabase.from("device_installations")
            .select {
                filter {
                    eq("restaurant_id", restaurantId.value)
                    eq("installation_id", installationId)
                }
            }
            .decodeSingleOrNull<DeviceInstallationDto>()

        if (existing != null) {
            return@deviceInstallationOperation activeDeviceOrThrow(existing)
        }

        val insert = DeviceInstallationInsertDto(
            restaurantId = restaurantId.value,
            userId = user.id,
            installationId = installationId,
            platform = PLATFORM_ANDROID,
            deviceName = buildDeviceName(
                manufacturer = deviceInfoProvider.manufacturer,
                model = deviceInfoProvider.model
            ),
            appVersion = appVersionProvider.versionName
        )

        supabase.from("device_installations")
            .insert(insert) {
                select()
            }
            .decodeSingle<DeviceInstallationDto>()
            .let(::activeDeviceOrThrow)
    }

    private companion object {
        const val PLATFORM_ANDROID = "android"
    }
}

private suspend inline fun <T> deviceInstallationOperation(
    crossinline operation: suspend () -> T
): Result<T> = try {
    Result.success(operation())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    Result.failure(DeviceInstallationOperationException())
}
