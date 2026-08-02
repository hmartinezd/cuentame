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
        tableMetadata = BackupFormatV1Contract.expectedTablesForSchema(2).associate { it to TableMetadata(if (it == "restaurants") 1 else 0, it in BackupFormatV1Contract.DERIVED_TABLES) },
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments")
    )

    private fun createStructuralValidChecksums(extra: Map<String, String> = emptyMap()): Map<String, String> {
        return mapOf(
            BackupFormatV1Contract.MANIFEST_ENTRY to "a".repeat(64),
            BackupFormatV1Contract.DATABASE_ENTRY to "a".repeat(64),
            BackupFormatV1Contract.PREFERENCES_ENTRY to "a".repeat(64),
            BackupFormatV1Contract.CHECKSUMS_ENTRY to "a".repeat(64)
        ) + extra
    }

    private fun createStructuralValidSizes(extra: Map<String, Long> = emptyMap()): Map<String, Long> {
        return mapOf(
            BackupFormatV1Contract.MANIFEST_ENTRY to 100L,
            BackupFormatV1Contract.DATABASE_ENTRY to 100L,
            BackupFormatV1Contract.PREFERENCES_ENTRY to 100L,
            BackupFormatV1Contract.CHECKSUMS_ENTRY to 100L
        ) + extra
    }

    @Test
    fun `schema 2 manifest with schema 3 metadata fails`() {
        val manifest = createValidBaseManifest().copy(
            databaseSchemaVersion = 2,
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("preparation_recipes", TableMetadata(0, false))
            }
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `schema 2 manifest with schema 4 metadata fails`() {
        val manifest = createValidBaseManifest().copy(
            databaseSchemaVersion = 2,
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("production_batches", TableMetadata(0, false))
            }
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `schema 3 manifest missing recipe metadata fails`() {
        val manifest = createValidBaseManifest().copy(
            databaseSchemaVersion = 3,
            tableMetadata = createValidBaseManifest().tableMetadata.filterKeys { 
                it != "preparation_recipes" && it != "preparation_recipe_components"
            }
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `schema 3 manifest with schema 4 metadata fails`() {
        val tables = BackupFormatV1Contract.expectedTablesForSchema(3).associateWith { TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        val manifest = createValidBaseManifest().copy(
            databaseSchemaVersion = 3,
            tableMetadata = tables.toMutableMap().apply {
                put("production_batches", TableMetadata(0, false))
            }
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `valid schema 4 manifest structure succeeds`() {
        val tables = BackupFormatV1Contract.expectedTablesForSchema(4).associateWith { TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        val manifest = createValidBaseManifest().copy(
            databaseSchemaVersion = 4,
            tableMetadata = tables
        )
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isNull()
    }

    @Test
    fun `unsupported format version fails`() {
        val manifest = createValidBaseManifest().copy(backupFormatVersion = 99)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.UnsupportedFormatVersion)
    }

    @Test
    fun `incompatible schema version fails`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 99)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.IncompatibleSchemaVersion)
    }

    @Test
    fun `unsupported checksum algorithm fails`() {
        val manifest = createValidBaseManifest().copy(checksumAlgorithm = "SHA-512")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `missing identity fields fails`() {
        val manifest = createValidBaseManifest().copy(restaurantId = "", restaurantName = "")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank restaurant ID fails`() {
        val manifest = createValidBaseManifest().copy(restaurantId = "")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank restaurant name fails`() {
        val manifest = createValidBaseManifest().copy(restaurantName = " ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank locale tag fails`() {
        val manifest = createValidBaseManifest().copy(localeTag = " ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank currency code fails`() {
        val manifest = createValidBaseManifest().copy(currencyCode = "   ")
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `missing required section fails`() {
        val manifest = createValidBaseManifest().copy(includedSections = listOf("data"))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `duplicate included section fails`() {
        val manifest = createValidBaseManifest().copy(includedSections = listOf("data", "preferences", "attachments", "data"))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `missing expected tables fails`() {
        val tables = createValidBaseManifest().tableMetadata.toMutableMap()
        tables.remove("restaurants")
        val manifest = createValidBaseManifest().copy(tableMetadata = tables)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `unexpected table metadata fails`() {
        val tables = createValidBaseManifest().tableMetadata.toMutableMap()
        tables["unknown_table"] = TableMetadata(0, false)
        val manifest = createValidBaseManifest().copy(tableMetadata = tables)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `negative table count fails`() {
        val tables = createValidBaseManifest().tableMetadata.toMutableMap()
        tables["restaurants"] = TableMetadata(-1, false)
        val manifest = createValidBaseManifest().copy(tableMetadata = tables)
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment metadata fails`() {
        val att = createValidAttachment(attachmentId = "short")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment ID fails`() {
        val att = createValidAttachment(attachmentId = "invalid!")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `duplicate attachment ID fails`() {
        val att1 = createValidAttachment(attachmentId = "0123456789abcdef")
        val att2 = createValidAttachment(attachmentId = "0123456789abcdef", archivePath = "attachments/other.jpg")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att1, att2))
        val checksums = createStructuralValidChecksums(mapOf(
            att1.archivePath to att1.checksumSha256,
            att2.archivePath to att2.checksumSha256
        ))
        val sizes = createStructuralValidSizes(mapOf(
            att1.archivePath to att1.sizeBytes,
            att2.archivePath to att2.sizeBytes
        ))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid canonical attachment path fails`() {
        val att = createValidAttachment(archivePath = "outside/file.jpg")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `duplicate attachment path fails`() {
        val att1 = createValidAttachment(attachmentId = "0123456789abcdef")
        val att2 = createValidAttachment(attachmentId = "fedcba9876543210", archivePath = att1.archivePath)
        val manifest = createValidBaseManifest().copy(attachments = listOf(att1, att2))
        val checksums = createStructuralValidChecksums(mapOf(att1.archivePath to att1.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att1.archivePath to att1.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment display name fails`() {
        val att = createValidAttachment(displayName = "")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `negative attachment size fails`() {
        val att = createValidAttachment(sizeBytes = -1)
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to 100L))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `invalid attachment checksum fails`() {
        val att = createValidAttachment(checksumSha256 = "short")
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `empty attachment reference list fails`() {
        val att = createValidAttachment(referencedBy = emptyList())
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `duplicate attachment reference fails`() {
        val ref = BackupAttachmentReference("WASTE_EVENT", "w1")
        val att = createValidAttachment(referencedBy = listOf(ref, ref))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `blank attachment reference record ID fails`() {
        val att = createValidAttachment(referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `unsupported attachment reference type fails`() {
        val att = createValidAttachment(referencedBy = listOf(BackupAttachmentReference("UNKNOWN", "id1")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)
        assertThat(failure).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `manifest to zip attachment bijection exact match succeeds`() {
        val att = createValidAttachment()
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isNull()
    }

    @Test
    fun `manifest to zip attachment bijection enforced`() {
        val att = createValidAttachment()
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        
        // 1. Missing from ZIP
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, createStructuralValidChecksums(), createStructuralValidSizes())).isEqualTo(BackupRestoreFailure.ManifestMismatch)
        
        // 2. Extra in ZIP
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256, "attachments/extra/file.jpg" to "a".repeat(64)))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes, "attachments/extra/file.jpg" to 100L))
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `manifest attachment missing from ZIP fails`() {
        val att = createValidAttachment()
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums()
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, createStructuralValidSizes())).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `ZIP attachment absent from manifest fails`() {
        val manifest = createValidBaseManifest()
        val checksums = createStructuralValidChecksums(mapOf("attachments/0123456789abcdef/file.jpg" to "a".repeat(64)))
        val sizes = createStructuralValidSizes(mapOf("attachments/0123456789abcdef/file.jpg" to 100L))
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `extra file in ZIP fails`() {
        val manifest = createValidBaseManifest()
        val checksums = createStructuralValidChecksums(mapOf("attachments/extra/file.jpg" to "b".repeat(64)))
        val sizes = createStructuralValidSizes(mapOf("attachments/extra/file.jpg" to 100L))
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `extra entry in manifest fails`() {
        val att = createValidAttachment()
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums()
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, createStructuralValidSizes())).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `unexpected non-attachment ZIP payload fails`() {
        val manifest = createValidBaseManifest()
        val checksums = createStructuralValidChecksums(mapOf("unexpected.txt" to "b".repeat(64)))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, createStructuralValidSizes(mapOf("unexpected.txt" to 100L)))
        assertThat(failure).isEqualTo(BackupRestoreFailure.UnexpectedEntry)
    }

    @Test
    fun `unexpected non-attachment entry rejected`() {
        val manifest = createValidBaseManifest()
        val checksums = createStructuralValidChecksums(mapOf("unexpected.txt" to "b".repeat(64)))
        val failure = BackupManifestContractValidator.validateManifestStructure(manifest, checksums, createStructuralValidSizes(mapOf("unexpected.txt" to 100L)))
        assertThat(failure).isEqualTo(BackupRestoreFailure.UnexpectedEntry)
    }

    @Test
    fun `attachment size mismatch fails`() {
        val att = createValidAttachment(sizeBytes = 100)
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to 200L))
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.AttachmentMismatch)
    }

    @Test
    fun `physical attachment size mismatch fails`() {
        val att = createValidAttachment(sizeBytes = 100)
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to att.checksumSha256))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to 200L))
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.AttachmentMismatch)
    }

    @Test
    fun `physical attachment checksum mismatch fails`() {
        val att = createValidAttachment(checksumSha256 = "a".repeat(64))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val checksums = createStructuralValidChecksums(mapOf(att.archivePath to "b".repeat(64)))
        val sizes = createStructuralValidSizes(mapOf(att.archivePath to att.sizeBytes))
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)).isEqualTo(BackupRestoreFailure.AttachmentMismatch)
    }

    @Test
    fun `bi-directional attachment relationship validation`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        
        // 1. Snapshot missing attachment reference
        val snapshotMissing = createValidEmptySnapshot()
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshotMissing)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
        
        // 2. Snapshot has attachment but manifest missing it
        val manifestEmpty = createValidBaseManifest()
        val snapshotExtra = createValidEmptySnapshot().copy(wasteEvents = listOf(createWasteEvent("w1", attId)))
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifestEmpty, snapshotExtra)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `bi-directional attachment relationship exact match succeeds`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1")))
        
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

    @Test
    fun `snapshot attachment missing from manifest fails`() {
        val manifest = createValidBaseManifest()
        val snapshot = createValidEmptySnapshot().copy(
            wasteEvents = listOf(createWasteEvent("w1", "0123456789abcdef"))
        )
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `manifest reference missing from snapshot fails`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "missing")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val snapshot = createValidEmptySnapshot()
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `snapshot missing referenced record fails`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "missing")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val snapshot = createValidEmptySnapshot()
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `manifest reference points to a record with a different attachment ID`() {
        val attIdA = "000000000000000a"
        val attIdB = "000000000000000b"
        val att = createValidAttachment(attachmentId = attIdA, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        
        val snapshot = createValidEmptySnapshot().copy(
            wasteEvents = listOf(createWasteEvent("w1", attIdB))
        )
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `missing purchase record reference fails`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "p1")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val snapshot = createValidEmptySnapshot() // missing p1
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `missing waste record reference fails`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(attachmentId = attId, referencedBy = listOf(BackupAttachmentReference("WASTE_EVENT", "w1")))
        val manifest = createValidBaseManifest().copy(attachments = listOf(att))
        val snapshot = createValidEmptySnapshot() // missing w1
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `shared attachment referenced by purchase and waste succeeds`() {
        val attId = "0123456789abcdef"
        val att = createValidAttachment(
            attachmentId = attId, 
            referencedBy = listOf(
                BackupAttachmentReference("PURCHASE_RECEIPT", "p1"),
                BackupAttachmentReference("WASTE_EVENT", "w1")
            )
        )
        val manifest = createValidBaseManifest().copy(
            attachments = listOf(att),
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(1, false))
                put("purchase_receipts", TableMetadata(1, false))
                put("waste_events", TableMetadata(1, false))
            }
        )
        val snapshot = createValidEmptySnapshot().copy(
            purchaseReceipts = listOf(createPurchaseReceipt("p1", attId)),
            wasteEvents = listOf(createWasteEvent("w1", attId))
        )
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isNull()
    }

    @Test
    fun `snapshot table count mismatch fails`() {
        val manifest = createValidBaseManifest().copy(
            tableMetadata = createValidBaseManifest().tableMetadata.toMutableMap().apply {
                put("restaurants", TableMetadata(5, false))
            }
        )
        val snapshot = createValidEmptySnapshot()
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `schema 2 manifest with non-empty recipe payload fails`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 2)
        val snapshot = createValidEmptySnapshot().copy(
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", "rest-1", "i1", "R", "r", "1", "1", null, "DRAFT", null, 0, 0, null)
            )
        )
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `schema 2 manifest with non-empty production payload fails`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 2)
        val snapshot = createValidEmptySnapshot().copy(
            productionBatches = listOf(
                ProductionBatchBackupDto("b1", "rest-1", "r1", "R", "i1", "1", "1", "1", "o1", "1", "1", "1", "1", "o1", "a1", false, null, null, 0, "DRAFT", null, 0, 0, null, null)
            )
        )
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `schema 3 manifest with non-empty production payload fails`() {
        val tables = BackupFormatV1Contract.expectedTablesForSchema(3).associateWith { TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 3, tableMetadata = tables)
        val snapshot = createValidEmptySnapshot().copy(
            productionBatches = listOf(
                ProductionBatchBackupDto("b1", "rest-1", "r1", "R", "i1", "1", "1", "1", "o1", "1", "1", "1", "1", "o1", "a1", false, null, null, 0, "DRAFT", null, 0, 0, null, null)
            )
        )
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `schema 2 manifest with non-empty component payload fails`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 2)
        val snapshot = createValidEmptySnapshot().copy(
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("c1", "r1", "i1", "o1", "1", "1", 0, null, 0, 0)
            )
        )
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun `schema 2 manifest with empty recipe arrays succeeds`() {
        val manifest = createValidBaseManifest().copy(databaseSchemaVersion = 2)
        val snapshot = createValidEmptySnapshot().copy(
            preparationRecipes = emptyList(),
            preparationRecipeComponents = emptyList()
        )
        val failure = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(failure).isNull()
    }

    private fun createValidAttachment(
        attachmentId: String = "0123456789abcdef",
        displayName: String = "file.jpg",
        archivePath: String = BackupFormatV1Contract.attachmentArchivePath(attachmentId, displayName),
        sizeBytes: Long = 100L,
        checksumSha256: String = "a".repeat(64),
        referencedBy: List<BackupAttachmentReference> = listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
    ) = BackupAttachmentMetadata(
        attachmentId = attachmentId,
        archivePath = archivePath,
        displayName = displayName,
        mimeType = null,
        sizeBytes = sizeBytes,
        checksumSha256 = checksumSha256,
        referencedBy = referencedBy
    )

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

    private fun createWasteEvent(id: String, attId: String?) = WasteEventBackupDto(
        id, "rest-1", "i1", "a1", "o1", "1", "1", "OTHER", 0, null, attId, "POSTED", 0, 0, 0, null
    )

    private fun createPurchaseReceipt(id: String, attId: String?) = PurchaseReceiptBackupDto(
        id = id,
        restaurantId = "rest-1",
        supplierId = "s1",
        invoiceNumber = "inv-1",
        purchaseDate = 1704110400000L,
        status = "POSTED",
        notes = null,
        attachmentId = attId,
        createdAt = 1704110400000L,
        updatedAt = 1704110400000L,
        postedAt = 1704110400000L,
        voidedAt = null
    )
}
