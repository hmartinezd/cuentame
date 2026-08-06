package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.*
import org.junit.Test

class BackupManifestValidatorTest {

    private fun createValidManifest() = BackupManifest(
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

    private fun createValidTableMetadata() = mapOf(
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
    ).toSortedMap()

    @Test
    fun `valid manifest passes`() {
        val result = BackupManifestValidator.validate(createValidManifest())
        assertThat(result).isInstanceOf(BackupValidationResult.Valid::class.java)
    }

    @Test
    fun `supports format versions 1 and 2`() {
        val v1 = createValidManifest().copy(backupFormatVersion = 1)
        assertThat(BackupManifestValidator.validate(v1)).isInstanceOf(BackupValidationResult.Valid::class.java)

        val v2 = createValidManifest().copy(backupFormatVersion = 2)
        assertThat(BackupManifestValidator.validate(v2)).isInstanceOf(BackupValidationResult.Valid::class.java)
    }

    @Test
    fun `rejects unsupported format version`() {
        val manifest = createValidManifest().copy(backupFormatVersion = 99)
        val result = BackupManifestValidator.validate(manifest) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MANIFEST_INVALID)
        assertThat(result.diagnostic).isEqualTo(BackupValidationDiagnostic.VERSION_MISMATCH)
    }

    @Test
    fun `rejects overlong attachment list`() {
        val manyAttachments = (1..BackupLimits.MAX_ATTACHMENT_COUNT + 1).map {
            val id = it.toString().padStart(16, '0')
            BackupAttachmentMetadata(
                attachmentId = id,
                archivePath = "attachments/$id/file.jpg",
                displayName = "file.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 10L,
                checksumSha256 = "0".repeat(64),
                referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
            )
        }
        val manifest = createValidManifest().copy(backupFormatVersion = 2, attachments = manyAttachments)
        val result = BackupManifestValidator.validate(manifest) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.LIMIT_EXCEEDED)
    }

    @Test
    fun `rejects invalid locale`() {
        val manifest = createValidManifest().copy(localeTag = "fr-FR")
        val result = BackupManifestValidator.validate(manifest) as BackupValidationResult.Invalid
        assertThat(result.diagnostic).isEqualTo(BackupValidationDiagnostic.LOCALE_UNSUPPORTED)
    }

    @Test
    fun `rejects missing table metadata`() {
        val manifest = createValidManifest().copy(tableMetadata = emptyMap())
        val result = BackupManifestValidator.validate(manifest) as BackupValidationResult.Invalid
        assertThat(result.diagnostic).isEqualTo(BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
    }
}
