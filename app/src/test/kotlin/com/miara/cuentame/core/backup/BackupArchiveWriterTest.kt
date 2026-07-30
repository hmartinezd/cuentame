package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.FakeBackupAttachmentSource
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupArchiveWriterTest {

    private val attachmentSource = FakeBackupAttachmentSource()
    private lateinit var writer: DefaultBackupArchiveWriter

    @Before
    fun setup() {
        writer = DefaultBackupArchiveWriter(attachmentSource)
    }

    private fun createValidMinimalPlan(
        snapshotBytes: ByteArray = "{}".toByteArray(),
        prefsBytes: ByteArray = "{}".toByteArray(),
        manifestBytes: ByteArray = "{\"v\":1}".toByteArray()
    ): BackupPlan {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        val manifest = mockk<BackupManifest>(relaxed = true) {
            every { localeTag } returns "en-US"
            every { attachments } returns emptyList()
            every { tableMetadata } returns emptyMap()
            every { databaseSchemaVersion } returns 2
            every { backupFormatVersion } returns 1
        }
        
        val snapshotSha = ImmutableBackupBytes.from(snapshotBytes).sha256()
        val prefsSha = ImmutableBackupBytes.from(prefsBytes).sha256()
        val manifestSha = ImmutableBackupBytes.from(manifestBytes).sha256()

        val checksumsMap = mapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to snapshotSha,
            BackupFormatV1Contract.PREFERENCES_ENTRY to prefsSha,
            BackupFormatV1Contract.MANIFEST_ENTRY to manifestSha
        )
        val checksumsJsonStr = "{\"data/database.json\":\"$snapshotSha\",\"manifest.json\":\"$manifestSha\",\"preferences/settings.json\":\"$prefsSha\"}"
        val checksumsBytes = checksumsJsonStr.toByteArray()

        return BackupPlan.create(
            snapshotDto = snapshot,
            snapshotJson = snapshotBytes,
            preferencesDto = prefs,
            preferencesJson = prefsBytes,
            attachments = emptyList(),
            manifest = manifest,
            manifestJson = manifestBytes,
            expectedEntryChecksums = checksumsMap,
            checksumsJson = checksumsBytes,
            totalUncompressedBytes = (snapshotBytes.size.toLong() + prefsBytes.size + manifestBytes.size + checksumsBytes.size)
        )
    }

    @Test
    fun `writer releases resources and does not close underlying stream`() = runTest {
        val output = object : ByteArrayOutputStream() {
            var closedCount = 0
            override fun close() {
                closedCount++
                super.close()
            }
        }
        
        val plan = createValidMinimalPlan()
        val result = writer.write(output, plan)
        
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)
        assertThat(output.closedCount).isEqualTo(0)
    }

    @Test
    fun `writer rejects plan whose calculated total exceeds configured limit during prevalidation`() = runTest {
        val smallLimits = BackupWriteLimits(maxTotalUncompressedBytes = 10L)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, smallLimits)
        
        val plan = createValidMinimalPlan() // Total size is larger than 10
        
        val output = ByteArrayOutputStream()
        val result = customWriter.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.LimitExceeded)
    }

    @Test
    fun `preferences payload checksum mismatch rejected`() = runTest {
        val validPlan = createValidMinimalPlan()
        val corruptedPrefs = ImmutableBackupBytes.from("corrupted".toByteArray())
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns corruptedPrefs
            every { manifestJson } returns validPlan.manifestJson
            every { checksumsJson } returns validPlan.checksumsJson
            every { attachments } returns emptyList()
            every { expectedEntryChecksums } returns validPlan.expectedEntryChecksums
            every { totalUncompressedBytes } returns validPlan.totalUncompressedBytes
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.ChecksumInconsistency)
    }

    @Test
    fun `runtime cumulative limit exceeded`() = runTest {
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = 5L)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
        // Entry sizes: snapshot=2, prefs=2, manifest=2. Cumulative will hit 6 > 5 during manifest write.
        val plan = createValidMinimalPlan(
            snapshotBytes = "{}".toByteArray(),
            prefsBytes = "{}".toByteArray(),
            manifestBytes = "{}".toByteArray()
        )
        
        val output = ByteArrayOutputStream()
        val result = customWriter.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.LimitExceeded)
    }

    @Test
    fun `exact total limit succeeds`() = runTest {
        val snapshotBytes = "123".toByteArray()
        val prefsBytes = "456".toByteArray()
        val manifestBytes = "789".toByteArray()
        val checksumsMap = mapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to ImmutableBackupBytes.from(snapshotBytes).sha256(),
            BackupFormatV1Contract.MANIFEST_ENTRY to ImmutableBackupBytes.from(manifestBytes).sha256(),
            BackupFormatV1Contract.PREFERENCES_ENTRY to ImmutableBackupBytes.from(prefsBytes).sha256()
        )
        val checksumsBytes = "{\"data/database.json\":\"${checksumsMap[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${checksumsMap[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${checksumsMap[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()
        val total = (snapshotBytes.size + prefsBytes.size + manifestBytes.size + checksumsBytes.size).toLong()
        
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = total)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns ImmutableBackupBytes.from(snapshotBytes)
            every { preferencesJson } returns ImmutableBackupBytes.from(prefsBytes)
            every { manifestJson } returns ImmutableBackupBytes.from(manifestBytes)
            every { checksumsJson } returns ImmutableBackupBytes.from(checksumsBytes)
            every { attachments } returns emptyList()
            every { expectedEntryChecksums } returns checksumsMap
            every { totalUncompressedBytes } returns total
        }

        val output = ByteArrayOutputStream()
        val result = customWriter.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)
    }

    @Test
    fun `attachment checksum map disagreement rejected`() = runTest {
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val data = "data".toByteArray()
        val hash = ImmutableBackupBytes.from(data).sha256()
        
        val plannedAtt = PlannedBackupAttachment.create(
            attUri, attId, "attachments/$attId/a.jpg", "a.jpg", null, data.size.toLong(), hash,
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        val validPlan = createValidMinimalPlan()
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns validPlan.preferencesJson
            every { manifestJson } returns validPlan.manifestJson
            every { attachments } returns listOf(plannedAtt)
            // Prevalidation checks that expectedEntryChecksums.keys == CORE_ENTRIES + attachments.paths
            // and that checksumsJson contains the same map.
            val map = validPlan.expectedEntryChecksums.toMutableMap().apply {
                put(plannedAtt.archivePath, hash)
            }
            every { expectedEntryChecksums } returns map
            
            // checksumsJson will mismatch the hash in the map (or vice versa)
            val corruptedJson = "{\"attachments/$attId/a.jpg\":\"${"0".repeat(64)}\",\"data/database.json\":\"${map[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${map[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${map[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()
            every { checksumsJson } returns ImmutableBackupBytes.from(corruptedJson)
            
            every { totalUncompressedBytes } returns validPlan.totalUncompressedBytes + data.size + (corruptedJson.size - validPlan.checksumsJson.size)
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.ChecksumInconsistency)
    }

    @Test
    fun `attachment disappears after planning results in failure`() = runTest {
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("missing")
        val plannedHash = "a".repeat(64)
        val plannedAtt = PlannedBackupAttachment.create(
            attUri, attId, "attachments/$attId/a.jpg", "a.jpg", null, 100L, plannedHash,
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        val plan = createValidMinimalPlan()
        
        val checksumsMap = plan.expectedEntryChecksums.toMutableMap().apply {
            put(plannedAtt.archivePath, plannedHash)
        }
        val checksumsJsonBytes = "{\"attachments/$attId/a.jpg\":\"$plannedHash\",\"data/database.json\":\"${checksumsMap[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${checksumsMap[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${checksumsMap[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()

        val mockedPlan = mockk<BackupPlan> {
            every { snapshotJson } returns plan.snapshotJson
            every { preferencesJson } returns plan.preferencesJson
            every { manifestJson } returns plan.manifestJson
            every { attachments } returns listOf(plannedAtt)
            every { expectedEntryChecksums } returns checksumsMap
            every { checksumsJson } returns ImmutableBackupBytes.from(checksumsJsonBytes)
            every { totalUncompressedBytes } returns plan.totalUncompressedBytes + 100 + (checksumsJsonBytes.size - plan.checksumsJson.size)
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, mockedPlan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.AttachmentUnreadable)
    }
}
