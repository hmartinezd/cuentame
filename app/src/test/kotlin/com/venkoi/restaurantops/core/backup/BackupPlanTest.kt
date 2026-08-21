package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.model.backup.BackupAttachmentMetadata
import com.venkoi.restaurantops.core.model.backup.BackupAttachmentReference
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupPlanTest {

    private fun createMinimalBaseManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.venkoi.restaurantops",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = "rest-1",
        restaurantName = "R",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createMinimalBasePlan(
        snapshotBytes: ByteArray = "{}".toByteArray(),
        prefsBytes: ByteArray = "{}".toByteArray(),
        manifest: BackupManifest = createMinimalBaseManifest(),
        checksumsBytes: ByteArray = "{}".toByteArray(),
        attachments: List<PlannedBackupAttachment> = emptyList(),
        checksumsMap: Map<String, String>? = null
    ): BackupPlan {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val manifestBytes = "{\"v\":1}".toByteArray()
        val sSha = ImmutableBackupBytes.from(snapshotBytes).sha256()
        val pSha = ImmutableBackupBytes.from(prefsBytes).sha256()
        val mSha = ImmutableBackupBytes.from(manifestBytes).sha256()

        val map = checksumsMap ?: mutableMapOf<String, String>().apply {
            put(BackupFormatV1Contract.DATABASE_ENTRY, sSha)
            put(BackupFormatV1Contract.PREFERENCES_ENTRY, pSha)
            put(BackupFormatV1Contract.MANIFEST_ENTRY, mSha)
            attachments.forEach { put(it.archivePath, it.checksumSha256) }
        }

        return BackupPlan.create(
            snapshotDto = snapshot,
            snapshotJson = snapshotBytes,
            preferencesDto = prefs,
            preferencesJson = prefsBytes,
            attachments = attachments,
            manifest = manifest,
            manifestJson = manifestBytes,
            expectedEntryChecksums = map,
            checksumsJson = checksumsBytes,
            totalUncompressedBytes = (snapshotBytes.size.toLong() + prefsBytes.size + manifestBytes.size + checksumsBytes.size + attachments.sumOf { it.sizeBytes })
        )
    }

    @Test
    fun `create with valid data succeeds`() {
        val plan = createMinimalBasePlan()
        assertThat(plan.totalUncompressedBytes).isAtLeast(0L)
    }

    @Test
    fun `plan is immutable and performs defensive copies`() {
        val bytes = "original".toByteArray()
        val plan = createMinimalBasePlan(snapshotBytes = bytes)
        
        // Mutate original array
        bytes[0] = 'X'.code.toByte()
        
        // Plan should be unchanged
        assertThat(plan.snapshotJson.copyForTest()).isEqualTo("original".toByteArray())
    }

    @Test
    fun `exposed collections cannot mutate plan`() {
        val plan = createMinimalBasePlan()
        
        assertThrows(UnsupportedOperationException::class.java) {
            (plan.attachments as MutableList).clear()
        }
        
        assertThrows(UnsupportedOperationException::class.java) {
            (plan.expectedEntryChecksums as MutableMap).clear()
        }
    }

    @Test
    fun `original attachment list mutation cannot change plan`() {
        val mutableAttachments = mutableListOf<PlannedBackupAttachment>()
        val plan = createMinimalBasePlan(attachments = mutableAttachments)
        
        // Can't use mockk for internal class easily here, just create a real one
        val att = PlannedBackupAttachment.create(
            AttachmentSourceUri("u"), "0123456789abcdef", "attachments/0123456789abcdef/n", "n", null, 0, "a".repeat(64),
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        mutableAttachments.add(att)
        assertThat(plan.attachments).isEmpty()
    }

    @Test
    fun `original manifest lists mutation cannot change plan`() {
        val reference = BackupAttachmentReference(recordType = "WASTE_EVENT", recordId = "w1")
        val references = mutableListOf(reference)
        val attachmentId = "0123456789abcdef"

        val plannedAttachment = PlannedBackupAttachment.create(
            sourceUri = AttachmentSourceUri("content://attachment"),
            attachmentId = attachmentId,
            archivePath = "attachments/$attachmentId/n",
            displayName = "n",
            mimeType = null,
            sizeBytes = 0L,
            checksumSha256 = "a".repeat(64),
            references = references
        )

        val manifestAttachment = BackupAttachmentMetadata(
            attachmentId = attachmentId,
            archivePath = plannedAttachment.archivePath,
            displayName = plannedAttachment.displayName,
            mimeType = plannedAttachment.mimeType,
            sizeBytes = plannedAttachment.sizeBytes,
            checksumSha256 = plannedAttachment.checksumSha256,
            referencedBy = references
        )

        val manifest = createMinimalBaseManifest().copy(attachments = listOf(manifestAttachment))
        val plan = createMinimalBasePlan(attachments = listOf(plannedAttachment), manifest = manifest)

        references.clear()
        assertThat(plan.manifest.attachments[0].referencedBy).hasSize(1)
    }

    @Test
    fun `create rejects total size mismatch`() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        val manifest = createMinimalBaseManifest()
        
        assertThrows(IllegalArgumentException::class.java) {
            BackupPlan.create(
                snapshot, "{}".toByteArray(), prefs, "{}".toByteArray(), emptyList(),
                manifest, "{}".toByteArray(), emptyMap(), "{}".toByteArray(), 9999L
            )
        }
    }

    @Test
    fun `rejects manifest metadata mismatch`() {
        val attId = "0123456789abcdef"
        val plannedAtt = PlannedBackupAttachment.create(
            AttachmentSourceUri("uri"), attId, "attachments/$attId/a.jpg", "a.jpg", null, 100L, "a".repeat(64),
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        // Manifest has different size for same attachment ID
        val manifest = createMinimalBaseManifest().copy(
            attachments = listOf(BackupAttachmentMetadata(attId, "attachments/$attId/a.jpg", "a.jpg", null, 200L, "a".repeat(64), listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))))
        )
        
        assertThrows(IllegalArgumentException::class.java) {
            createMinimalBasePlan(attachments = listOf(plannedAtt), manifest = manifest)
        }
    }

    @Test
    fun `rejects duplicate manifest attachment ID`() {
        val manifest = createMinimalBaseManifest().copy(
            attachments = listOf(
                BackupAttachmentMetadata("0123456789abcdef", "attachments/0123456789abcdef/n1", "n1", null, 0, "a".repeat(64), listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))),
                BackupAttachmentMetadata("0123456789abcdef", "attachments/0123456789abcdef/n2", "n2", null, 0, "a".repeat(64), listOf(BackupAttachmentReference("WASTE_EVENT", "w2")))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            createMinimalBasePlan(manifest = manifest)
        }
    }

    @Test
    fun `rejects duplicate manifest reference`() {
        val attId = "0123456789abcdef"
        val ref = BackupAttachmentReference("WASTE_EVENT", "w1")
        val manifest = createMinimalBaseManifest().copy(
            attachments = listOf(
                BackupAttachmentMetadata(attId, "attachments/$attId/n", "n", null, 0, "a".repeat(64), listOf(ref, ref.copy()))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            createMinimalBasePlan(manifest = manifest)
        }
    }

    @Test
    fun `rejects attachment checksum mismatch with expected map`() {
        val attId = "0123456789abcdef"
        val plannedAtt = PlannedBackupAttachment.create(
            AttachmentSourceUri("uri"), attId, "attachments/$attId/a.jpg", "a.jpg", null, 100L, "a".repeat(64),
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        val map = mutableMapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to "0".repeat(64),
            BackupFormatV1Contract.PREFERENCES_ENTRY to "0".repeat(64),
            BackupFormatV1Contract.MANIFEST_ENTRY to "0".repeat(64),
            plannedAtt.archivePath to "f".repeat(64) // mismatch
        )
        
        assertThrows(IllegalArgumentException::class.java) {
            createMinimalBasePlan(attachments = listOf(plannedAtt), checksumsMap = map)
        }
    }
}
