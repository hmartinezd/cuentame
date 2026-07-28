package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupAttachmentMetadata
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata
import org.junit.Test
import java.time.Instant

class BackupManifestValidatorTest {

    private val validTableMetadata = mapOf(
        "restaurants" to TableMetadata(1, false),
        "inventory_areas" to TableMetadata(1, false),
        "ingredient_categories" to TableMetadata(1, false),
        "units" to TableMetadata(1, false),
        "ingredients" to TableMetadata(1, false),
        "ingredient_unit_options" to TableMetadata(1, false),
        "suppliers" to TableMetadata(1, false),
        "purchase_receipts" to TableMetadata(1, false),
        "purchase_lines" to TableMetadata(1, false),
        "stock_counts" to TableMetadata(1, false),
        "stock_count_areas" to TableMetadata(1, false),
        "stock_count_lines" to TableMetadata(1, false),
        "waste_events" to TableMetadata(1, false),
        "inventory_movements" to TableMetadata(1, false),
        "inventory_balance_projections" to TableMetadata(1, true),
        "ingredient_cost_projections" to TableMetadata(1, true)
    )

    private val validSha = "a".repeat(64)

    private val validManifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = Instant.now().toString(),
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = "rest-1",
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = validTableMetadata,
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments")
    )

    @Test
    fun `validate accepts valid manifest`() {
        assertThat(BackupManifestValidator.validate(validManifest).isSuccess).isTrue()
    }

    @Test
    fun `validate accepts valid attachment`() {
        val manifest = validManifest.copy(
            attachments = listOf(
                BackupAttachmentMetadata(
                    attachmentId = "att-1",
                    archivePath = "attachments/att-1.jpg",
                    displayName = "Receipt Image",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024,
                    checksumSha256 = validSha,
                    referencedBy = listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "pr-1"))
                )
            )
        )
        assertThat(BackupManifestValidator.validate(manifest).isSuccess).isTrue()
    }

    @Test
    fun `validate rejects duplicate attachment ID`() {
        val att1 = BackupAttachmentMetadata("att-1", "attachments/att-1.jpg", "R1", "image/jpeg", 100, validSha, listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "pr-1")))
        val att2 = BackupAttachmentMetadata("att-1", "attachments/att-2.jpg", "R2", "image/jpeg", 200, validSha, listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "pr-2")))
        val manifest = validManifest.copy(attachments = listOf(att1, att2))
        val res = BackupManifestValidator.validate(manifest)
        assertThat(res.isFailure).isTrue()
        assertThat(res.exceptionOrNull()?.message).contains("Duplicate attachment ID")
    }

    @Test
    fun `validate rejects duplicate archive path`() {
        val att1 = BackupAttachmentMetadata("att-1", "attachments/same.jpg", "R1", "image/jpeg", 100, validSha, listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "pr-1")))
        val att2 = BackupAttachmentMetadata("att-2", "attachments/same.jpg", "R2", "image/jpeg", 200, validSha, listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "pr-2")))
        val manifest = validManifest.copy(attachments = listOf(att1, att2))
        val res = BackupManifestValidator.validate(manifest)
        assertThat(res.isFailure).isTrue()
        assertThat(res.exceptionOrNull()?.message).contains("Duplicate archive path")
    }

    @Test
    fun `validate rejects empty referencedBy list`() {
        val att = BackupAttachmentMetadata("att-1", "attachments/att-1.jpg", "R1", "image/jpeg", 100, validSha, emptyList())
        val manifest = validManifest.copy(attachments = listOf(att))
        val res = BackupManifestValidator.validate(manifest)
        assertThat(res.isFailure).isTrue()
        assertThat(res.exceptionOrNull()?.message).contains("referencedBy list cannot be empty")
    }

    @Test
    fun `validate rejects unsupported recordType in reference`() {
        val att = BackupAttachmentMetadata("att-1", "attachments/att-1.jpg", "R1", "image/jpeg", 100, validSha, listOf(BackupAttachmentReference("UNSUPPORTED_TYPE", "id-1")))
        val manifest = validManifest.copy(attachments = listOf(att))
        val res = BackupManifestValidator.validate(manifest)
        assertThat(res.isFailure).isTrue()
        assertThat(res.exceptionOrNull()?.message).contains("Unsupported recordType")
    }

    @Test
    fun `validate rejects duplicate reference in referencedBy`() {
        val ref = BackupAttachmentReference("PURCHASE_RECEIPT", "pr-1")
        val att = BackupAttachmentMetadata("att-1", "attachments/att-1.jpg", "R1", "image/jpeg", 100, validSha, listOf(ref, ref))
        val manifest = validManifest.copy(attachments = listOf(att))
        val res = BackupManifestValidator.validate(manifest)
        assertThat(res.isFailure).isTrue()
        assertThat(res.exceptionOrNull()?.message).contains("Duplicate reference in attachment referencedBy list")
    }
}
