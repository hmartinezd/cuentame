package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant

class BackupManifestTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `manifest serialization and deserialization`() {
        val now = Instant.now()
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = now.toString(),
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1L,
            databaseSchemaVersion = 1,
            restaurantId = "rest-1",
            restaurantName = "Test Restaurant",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = mapOf("ingredients" to TableMetadata(10, false)),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences")
        )

        val serialized = json.encodeToString(manifest)
        val deserialized = json.decodeFromString<BackupManifest>(serialized)

        assertThat(deserialized).isEqualTo(manifest)
        assertThat(deserialized.createdAtUtc).isEqualTo(now.toString())
    }
}
