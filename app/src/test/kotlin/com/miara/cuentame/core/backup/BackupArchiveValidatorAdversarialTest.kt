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

    @Before
    fun setup() {
        validator = DefaultBackupArchiveValidator(jsonCodecs)
    }

    @Test
    fun `positive control - valid archive passes`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = validator.validate(ByteArrayInputStream(zipBytes))
        assertThat(result).isInstanceOf(BackupValidationResult.Valid::class.java)
    }

    @Test
    fun `rejects archive with missing manifest`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .removeEntry("manifest.json")
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
    }

    @Test
    fun `rejects archive with unexpected entry`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("unexpected.txt", "hacker".toByteArray())
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNEXPECTED_ENTRY)
    }

    @Test
    fun `rejects archive with duplicate entry`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addDuplicateEntry("data/database.json", "{}".toByteArray())
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.DUPLICATE_ENTRY)
    }

    @Test
    fun `rejects archive with checksum key mismatch`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceRawChecksums("{\"data/database.json\":\"0000000000000000000000000000000000000000000000000000000000000000\"}")
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
    }

    @Test
    fun `rejects archive with checksum hash mismatch`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceRawChecksums("{\"data/database.json\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"manifest.json\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"preferences/settings.json\":\"0000000000000000000000000000000000000000000000000000000000000000\"}")
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun `rejects archive with overlong entry name`() {
        val longName = "a".repeat(BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES + 1)
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry(longName, "data".toByteArray())
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNSAFE_ENTRY_PATH)
    }

    @Test
    fun `rejects archive with malformed UTF-8 in manifest`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceFirstEntry("manifest.json", byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MANIFEST_INVALID)
    }

    @Test
    fun `rejects archive with database schema version mismatch`() {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = builder.createValidBaseManifest().copy(databaseSchemaVersion = 1)
        val zipBytes = builder
            .replaceManifest(manifest)
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MANIFEST_INVALID)
        assertThat(result.diagnostic).isEqualTo(BackupValidationDiagnostic.DATABASE_SCHEMA_MISMATCH)
    }
}

private fun BackupArchiveTestBuilder.createValidBaseManifest() = BackupManifest(
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
    tableMetadata = mapOf(
        "restaurants" to TableMetadata(1, false),
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
    ).toSortedMap(),
    attachments = emptyList(),
    includedSections = listOf("data", "preferences", "attachments").sorted()
)
