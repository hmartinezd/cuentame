package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.BackupJsonCodecs
import com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class BackupPreferencesCompatibilityTest {
    private val codecs = BackupJsonCodecs()

    @Test
    fun `old preferences JSON defaults menu management to enabled`() {
        val restored = codecs.reader.decodeFromString<BackupPreferencesDto>(
            """{"themeMode":"SYSTEM","dynamicColorEnabled":true,"appLocaleTag":"en-US"}"""
        )

        assertThat(restored.menuManagementEnabled).isTrue()
    }

    @Test
    fun `new preferences JSON explicitly contains menu management setting`() {
        val json = codecs.writer.encodeToString(
            BackupPreferencesDto("SYSTEM", true, "en-US", menuManagementEnabled = false)
        )

        assertThat(json).contains("\"menuManagementEnabled\": false")
    }
}
