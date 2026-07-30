package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.backup.model.*
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
        tableMetadata = BackupFormatV1Contract.EXPECTED_TABLES.associate { it to TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES) },
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
    fun `missing identity fields fails`() {
        val manifest = createValidBaseManifest().copy(restaurantName = "   ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `manifest to zip attachment bijection enforced`() {
        val attId = "0123456789abcdef"
        val path = "attachments/$attId/file.jpg"
        val att = BackupAttachmentMetadata(
            attachmentId = attId,
            archivePath = path,
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 100,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = mapOf(
            "data/database.json" to "a".repeat(64),
            "preferences/settings.json" to "a".repeat(64),
            "manifest.json" to "a".repeat(64),
            path to att.checksumSha256
        )
        val sizes = mapOf(
            "data/database.json" to 0L,
            "preferences/settings.json" to 0L,
            "manifest.json" to 0L,
            path to 100L
        )
        
        // 1. Exact match succeeds
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isNull()
        
        // 2. Extra file in ZIP fails
        val extraChecksums = checksums + ("attachments/extra/file.jpg" to "b".repeat(64))
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, extraChecksums, sizes)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
        
        // 3. Extra entry in manifest fails
        val missingChecksums = checksums - path
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, missingChecksums, sizes)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `snapshot table count mismatch fails`() {
        val manifest = createValidBaseManifest().copy(
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(5, false))
            }
        )
        val snapshot = createValidEmptySnapshot()
        
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `bi-directional attachment relationship validation`() {
        val attId = "0123456789abcdef"
        val att = BackupAttachmentMetadata(
            attachmentId = attId,
            archivePath = "attachments/$attId/file.jpg",
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 0,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        // 1. Exact match succeeds
        val manifest = createValidBaseManifest().copy(
            attachments = listOf(att),
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(1, false))
                put("waste_events", TableMetadata(1, false))
            }
        )
        val snapshot = createValidEmptySnapshot().copy(
            wasteEvents = listOf(createWasteEvent("w1", attId))
        )
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isNull()
        
        // 2. Snapshot has attachment not in manifest fails
        val snapshotExtra = snapshot.copy(wasteEvents = listOf(createWasteEvent("w1", "different-id")))
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshotExtra)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
        
        // 3. Manifest references non-existent snapshot record fails
        val manifestExtra = manifest.copy(attachments = listOf(att.copy(referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "missing")))))
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifestExtra, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    private fun createValidEmptySnapshot() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 0, 0, null)),
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
    
    @Test
    fun `unexpected non-attachment entry rejected`() {
        val manifest = createValidBaseManifest()
        val checksums = mapOf(
            "manifest.json" to "a".repeat(64),
            "data/database.json" to "a".repeat(64),
            "preferences/settings.json" to "a".repeat(64),
            "unexpected.txt" to "b".repeat(64) // Not allowed
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.UnexpectedEntry)
    }

    private fun createWasteEvent(id: String, attId: String?) = WasteEventBackupDto(
        id, "rest-1", "i1", "a1", "o1", "1", "1", "OTHER", 0, null, attId, "POSTED", 0, 0, 0, null
    )
}
