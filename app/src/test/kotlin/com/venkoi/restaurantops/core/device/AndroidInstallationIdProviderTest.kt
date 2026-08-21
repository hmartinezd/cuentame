package com.venkoi.restaurantops.core.device

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.UuidIdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AndroidInstallationIdProviderTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearInstallationIdentity() {
        context.getSharedPreferences("device_installation_identity", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `installation id remains stable across calls and provider recreation`() = runTest {
        val firstProvider = AndroidInstallationIdProvider(context, UuidIdGenerator())
        val first = firstProvider.getOrCreateInstallationId()

        assertThat(firstProvider.getOrCreateInstallationId()).isEqualTo(first)
        assertThat(AndroidInstallationIdProvider(context, UuidIdGenerator()).getOrCreateInstallationId())
            .isEqualTo(first)
    }

    @Test
    fun `generated installation id is uuid version four`() = runTest {
        val generated = AndroidInstallationIdProvider(context, UuidIdGenerator())
            .getOrCreateInstallationId()
        val uuid = UUID.fromString(generated)

        assertThat(uuid.version()).isEqualTo(4)
        assertThat(uuid.variant()).isEqualTo(2)
    }
}
