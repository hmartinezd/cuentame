package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupAttachmentMetadata
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.TableMetadata
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections

class BackupPlanTest {

    private fun createMinimalBaseManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
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
    fun `plan is immutable and performs defensive copies`() {
        val bytes = "original".toByteArray()
        val plan = createMinimalBasePlan(snapshotBytes = bytes)
        
        // Mutate original array
        bytes[0] = 'X'.code.toByte()
        
        // Plan should be unchanged
        assertThat(plan.snapshotJson.copyForTest()).isEqualTo("original".toByteArray())
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
                BackupAttachmentMetadata("id1", "attachments/id1/n1", "n1", null, 0, "a".repeat(64), listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))),
                BackupAttachmentMetadata("id1", "attachments/id1/n2", "n2", null, 0, "a".repeat(64), listOf(BackupAttachmentReference("WASTE_EVENT", "w2")))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            createMinimalBasePlan(manifest = manifest)
        }
    }
}
