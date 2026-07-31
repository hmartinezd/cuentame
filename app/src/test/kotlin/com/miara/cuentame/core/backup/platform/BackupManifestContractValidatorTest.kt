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
    fun `incompatible schema version fails`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 99)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.IncompatibleSchemaVersion)
    }

    @Test
    fun `blank restaurant ID fails`() {
        val manifest = createValidBaseManifest().copy(restaurantId = "")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank locale tag fails`() {
        val manifest = createValidBaseManifest().copy(localeTag = " ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank currency code fails`() {
        val manifest = createValidBaseManifest().copy(currencyCode = "   ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `negative table count fails`() {
        val manifest = createValidBaseManifest().copy(
            tableMetadata = mapOf("restaurants" to TableMetadata(-1, false))
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment ID fails`() {
        val att = BackupAttachmentMetadata("invalid!", "path", "name", null, 10, "sum", emptyList())
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `negative attachment size fails`() {
        val att = BackupAttachmentMetadata("att-1", "path", "name", null, -1, "sum", emptyList())
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, emptyMap(), emptyMap())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `manifest to zip attachment bijection exact match succeeds`() {
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
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isNull()
    }

    @Test
    fun `extra file in ZIP fails`() {
        val manifest = createValidBaseManifest()
        val checksums = mapOf(
            "data/database.json" to "a".repeat(64),
            "preferences/settings.json" to "a".repeat(64),
            "manifest.json" to "a".repeat(64),
            "attachments/extra/file.jpg" to "b".repeat(64)
        )
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, emptyMap())).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `extra entry in manifest fails`() {
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
            "manifest.json" to "a".repeat(64)
            // missing 'path'
        )
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, emptyMap())).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `snapshot attachment missing from manifest fails`() {
        val manifest = createValidBaseManifest() // No attachments
        val snapshot = createValidEmptySnapshot().copy(
            wasteEvents = listOf(createWasteEvent("w1", "some-id"))
        )
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `manifest reference missing from snapshot fails`() {
        val attId = "0123456789abcdef"
        val att = BackupAttachmentMetadata(
            attachmentId = attId,
            archivePath = "attachments/$attId/file.jpg",
            displayName = "file.jpg",
            mimeType = null,
            sizeBytes = 0,
            checksumSha256 = "a".repeat(64),
            referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "missing"))
        )
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val snapshot = createValidEmptySnapshot()
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `physical attachment size mismatch fails`() {
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
        val checksums = mapOf(path to att.checksumSha256)
        val sizes = mapOf(path to 200L) // Mismatch
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.AttachmentMismatch)
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
    fun `bi-directional attachment relationship exact match succeeds`() {
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
