package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.model.backup.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupArchiveValidatorAdversarialTest {

    private val jsonCodecs = BackupJsonCodecs()
    private lateinit var validator: DefaultBackupArchiveValidator
    private lateinit var builder: BackupArchiveTestBuilder

    @Before
    fun setup() {
        validator = DefaultBackupArchiveValidator(jsonCodecs)
        builder = BackupArchiveTestBuilder(jsonCodecs)
    }

    private fun createValidTableMetadata() = mapOf(
        "restaurants" to TableMetadata(0, false),
        "inventory_areas" to TableMetadata(0, false),
        "ingredient_categories" to TableMetadata(0, false),
        "units" to TableMetadata(0, false),
        "ingredients" to TableMetadata(0, false),
        "ingredient_unit_options" to TableMetadata(0, false),
        "suppliers" to TableMetadata(0, false),
        "purchase_receipts" to TableMetadata(0, false),
        "purchase_lines" to TableMetadata(0, false),
        "stock_counts" to TableMetadata(0, false),
        "stock_count_areas" to TableMetadata(0, false),
        "stock_count_lines" to TableMetadata(0, false),
        "waste_events" to TableMetadata(0, false),
        "inventory_movements" to TableMetadata(0, false),
        "inventory_balance_projections" to TableMetadata(0, true),
        "ingredient_cost_projections" to TableMetadata(0, true)
    ).entries.sortedBy { it.key }.associate { it.key to it.value }

    private fun createValidBaseManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = "rest-1",
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = createValidTableMetadata(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments").sorted()
    )

    private fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `rejects archive with unexpected entry`() {
        val manifest = createValidBaseManifest()
        val manifestJson = jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        val dbJson = jsonCodecs.writer.encodeToString(com.miara.cuentame.core.backup.model.BackupSnapshotDto.serializer(), BackupTestFixtures.createEmptySnapshotDto()).toByteArray()
        val settingsJson = jsonCodecs.writer.encodeToString(BackupPreferencesDto.serializer(), BackupPreferencesDto("SYSTEM", true, "en-US")).toByteArray()

        val checksums = mapOf(
            "manifest.json" to hash(manifestJson),
            "data/database.json" to hash(dbJson),
            "preferences/settings.json" to hash(settingsJson),
            "unexpected.txt" to hash("hacker".toByteArray())
        )

        val zipBytes = builder
            .withEntry("manifest.json", manifestJson)
            .withEntry("data/database.json", dbJson)
            .withEntry("preferences/settings.json", settingsJson)
            .withEntry("unexpected.txt", "hacker".toByteArray())
            .withChecksums(checksums) 
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNEXPECTED_ENTRY)
    }

    @Test
    fun `rejects archive with missing required entry`() {
        val zipBytes = builder
            .withEntry("data/database.json", "{}".toByteArray())
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
    }

    @Test
    fun `rejects archive with checksum key mismatch`() {
        val manifest = createValidBaseManifest()
        val manifestJson = jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        val dbJson = "{}".toByteArray()
        val settingsJson = "{}".toByteArray()
        
        val zipBytes = builder
            .withEntry("manifest.json", manifestJson)
            .withEntry("data/database.json", dbJson)
            .withEntry("preferences/settings.json", settingsJson)
            .withChecksums(mapOf("data/database.json" to hash(dbJson))) 
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
    }

    @Test
    fun `rejects archive with checksum hash mismatch`() {
        val manifest = createValidBaseManifest()
        val manifestJson = jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        val dbJson = "{}".toByteArray()
        val settingsJson = "{}".toByteArray()

        val checksums = mapOf(
            "manifest.json" to hash(manifestJson),
            "data/database.json" to hash(dbJson),
            "preferences/settings.json" to "0000000000000000000000000000000000000000000000000000000000000000"
        )

        val zipBytes = builder
            .withEntry("manifest.json", manifestJson)
            .withEntry("data/database.json", dbJson)
            .withEntry("preferences/settings.json", settingsJson)
            .withChecksums(checksums)
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_MISMATCH)
    }
}
