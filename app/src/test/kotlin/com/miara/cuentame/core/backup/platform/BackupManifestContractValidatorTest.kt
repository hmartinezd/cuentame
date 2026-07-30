package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.model.RestaurantBackupDto
import org.junit.Test

class BackupManifestContractValidatorTest {

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
        tableMetadata = BackupFormatV1Contract.EXPECTED_TABLES.associate { it to TableMetadata(0, false) },
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments")
    )

    @Test
    fun `valid manifest structure succeeds`() {
        val manifest = createValidBaseManifest()
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isNull()
    }

    @Test
    fun `unsupported format version fails`() {
        val manifest = createValidBaseManifest().copy(backupFormatVersion = 99)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.UnsupportedFormatVersion)
    }

    @Test
    fun `missing expected tables fails`() {
        val manifest = createValidBaseManifest().copy(tableMetadata = emptyMap())
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment metadata fails`() {
        val att = BackupAttachmentMetadata(
            attachmentId = "short", // invalid
            archivePath = "wrong/path",
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 100,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        
        val calculatedChecksums = mapOf(att.archivePath to att.checksumSha256)
        val calculatedSizes = mapOf(att.archivePath to 100L)
        
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, calculatedChecksums, calculatedSizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `attachment size mismatch fails`() {
        val attId = "0123456789abcdef"
        val att = BackupAttachmentMetadata(
            attachmentId = attId,
            archivePath = "attachments/$attId/file.jpg",
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 100,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        
        val failure = BackupManifestContractValidator.validateManifestStructure(
            manifest, 
            mapOf(att.archivePath to att.checksumSha256),
            mapOf(att.archivePath to 200L) // mismatch
        )
        assertThat(failure).isEqualTo(BackupRestoreFailure.AttachmentMismatch)
    }

    @Test
    fun `snapshot table count mismatch fails`() {
        val manifest = createValidBaseManifest().copy(
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(5, false)) // mismatch with actual 1
            }
        )
        val snapshot = createValidEmptySnapshot()
        
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `snapshot missing referenced record fails`() {
        val attId = "0123456789abcdef"
        val att = BackupAttachmentMetadata(
            attachmentId = attId,
            archivePath = "attachments/$attId/file.jpg",
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 0,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "missing-id"))
        )
        // Ensure table metadata matches the 1 restaurant in createValidEmptySnapshot()
        val manifest = createValidBaseManifest().copy(
            attachments = listOf(att),
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(1, false))
            }
        )
        val snapshot = createValidEmptySnapshot()
        
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isInstanceOf(BackupRestoreFailure.SnapshotIntegrityFailure::class.java)
    }

    private fun createValidEmptySnapshot() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto("rest-1", "R", "USD", "en-US", 0, 0, null)),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )
}
