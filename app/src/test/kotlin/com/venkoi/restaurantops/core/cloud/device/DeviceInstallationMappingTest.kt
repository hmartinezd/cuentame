package com.venkoi.restaurantops.core.cloud.device

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRepository
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRevokedException
import kotlin.coroutines.Continuation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceInstallationMappingTest {

    @Test
    fun `existing active device maps successfully`() {
        assertThat(activeDeviceOrThrow(deviceDto())).isEqualTo(
            DeviceInstallation(
                id = "device-row-1",
                restaurantId = RestaurantId("restaurant-1"),
                installationId = "installation-1"
            )
        )
    }

    @Test
    fun `revoked matching device fails safely`() {
        assertThrows(DeviceInstallationRevokedException::class.java) {
            activeDeviceOrThrow(deviceDto(revokedAt = "2026-08-21T12:00:00Z"))
        }
    }

    @Test
    fun `repository boundary preserves confirmed revocation failure`() = runTest {
        val result = deviceInstallationOperation {
            activeDeviceOrThrow(deviceDto(revokedAt = "2026-08-21T12:00:00Z"))
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(DeviceInstallationRevokedException::class.java)
    }

    @Test
    fun `device dto mapping preserves all domain identifiers`() {
        val mapped = deviceDto().toDomain()

        assertThat(mapped.id).isEqualTo("device-row-1")
        assertThat(mapped.restaurantId).isEqualTo(RestaurantId("restaurant-1"))
        assertThat(mapped.installationId).isEqualTo("installation-1")
    }

    @Test
    fun `device name construction is deterministic and avoids repeated manufacturer`() {
        assertThat(buildDeviceName("Google", "Pixel 10")).isEqualTo("Google Pixel 10")
        assertThat(buildDeviceName("Samsung", "SAMSUNG SM-X900")).isEqualTo("SAMSUNG SM-X900")
        assertThat(buildDeviceName("  Google  ", "  Pixel 10  ")).isEqualTo("Google Pixel 10")
    }

    @Test
    fun `public ensure method accepts restaurant identity only`() {
        val method = DeviceInstallationRepository::class.java.declaredMethods.single {
            it.name.startsWith("ensureCurrentDeviceRegistered")
        }

        assertThat(method.parameterTypes).hasLength(2)
        assertThat(method.parameterTypes[0]).isEqualTo(String::class.java)
        assertThat(Continuation::class.java.isAssignableFrom(method.parameterTypes[1])).isTrue()
    }

    private fun deviceDto(revokedAt: String? = null) = DeviceInstallationDto(
        id = "device-row-1",
        restaurantId = "restaurant-1",
        userId = "authenticated-user",
        installationId = "installation-1",
        platform = "android",
        deviceName = "Google Pixel 10",
        appVersion = "1.0",
        revokedAt = revokedAt
    )
}
