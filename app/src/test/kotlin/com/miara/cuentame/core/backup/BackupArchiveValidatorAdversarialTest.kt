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
            .removeEntry(BackupFormatV1Contract.MANIFEST_ENTRY)
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
    }

    @Test
    fun `rejects archive with duplicate entry`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addDuplicateEntry(BackupFormatV1Contract.DATABASE_ENTRY, "{}".toByteArray())
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
    fun `rejects archive with malformed UTF-8 manifest`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceFirstEntry(BackupFormatV1Contract.MANIFEST_ENTRY, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MANIFEST_INVALID)
    }

    @Test
    fun `rejects archive with schema version mismatch`() {
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

    @Test
    fun `rejects archive with checksum value mismatch`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceRawChecksums("{\"data/database.json\":\"${"a".repeat(64)}\",\"manifest.json\":\"${"a".repeat(64)}\",\"preferences/settings.json\":\"${"a".repeat(64)}\"}")
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun `rejects archive with traversal path`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addRawEntry("../traversal.json", "{}".toByteArray())
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNSAFE_ENTRY_PATH)
    }

    @Test
    fun `rejects archive with unexpected entry`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addRawEntry("unexpected.json", "{}".toByteArray())
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNEXPECTED_ENTRY)
    }
}

private fun BackupArchiveTestBuilder.createValidBaseManifest() = BackupManifest(
    backupFormatVersion = BackupFormatV1Contract.BACKUP_FORMAT_VERSION,
    createdAtUtc = "2026-01-01T12:00:00Z",
    applicationId = "com.miara.cuentame",
    appVersionName = "1.0",
    appVersionCode = 1L,
    databaseSchemaVersion = 2,
    restaurantId = "rest-1",
    restaurantName = "Test Rest",
    localeTag = "en-US",
    currencyCode = "USD",
    tableMetadata = BackupFormatV1Contract.expectedTablesForSchema(2).associateWith { 
        TableMetadata(if (it == "restaurants") 1 else 0, it in BackupFormatV1Contract.DERIVED_TABLES)
    }.toSortedMap(),
    attachments = emptyList(),
    includedSections = BackupFormatV1Contract.REQUIRED_SECTIONS.toList().sorted(),
    checksumAlgorithm = BackupFormatV1Contract.CHECKSUM_ALGORITHM
)
