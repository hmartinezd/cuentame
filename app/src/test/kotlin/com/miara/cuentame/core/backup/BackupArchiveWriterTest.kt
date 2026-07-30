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
    fun `database payload checksum mismatch rejected`() = runTest {
        val validPlan = createValidMinimalPlan()
        val corruptedSnapshot = ImmutableBackupBytes.from("corrupted".toByteArray())
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns corruptedSnapshot
            every { preferencesJson } returns validPlan.preferencesJson
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
    fun `writer rejects calculated total above configured limit during prevalidation`() = runTest {
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = 5L)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
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
    fun `runtime cumulative limit exceeded`() = runTest {
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val plannedSize = 10L
        val hash = "a".repeat(64)
        
        val plannedAtt = PlannedBackupAttachment.create(
            attUri, attId, "attachments/$attId/a.jpg", "a.jpg", null, plannedSize, hash,
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )
        
        val validPlan = createValidMinimalPlan()
        val map = validPlan.expectedEntryChecksums.toMutableMap().apply {
            put(plannedAtt.archivePath, hash)
        }
        val checksumsJsonBytes = "{\"attachments/$attId/a.jpg\":\"$hash\",\"data/database.json\":\"${map[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${map[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${map[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()

        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns validPlan.preferencesJson
            every { manifestJson } returns validPlan.manifestJson
            every { attachments } returns listOf(plannedAtt)
            every { expectedEntryChecksums } returns map
            every { checksumsJson } returns ImmutableBackupBytes.from(checksumsJsonBytes)
            every { totalUncompressedBytes } returns (validPlan.totalUncompressedBytes + plannedSize + (checksumsJsonBytes.size - validPlan.checksumsJson.size))
        }

        // Configure limit to fit exactly planned size
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = plan.totalUncompressedBytes)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
        // Fake streams WAY more than planned to ensure it crosses the cumulative limit
        attachmentSource.dataMap[attUri] = ByteArray(2000) 
        
        val output = object : ByteArrayOutputStream() {
            var closedCount = 0
            override fun close() {
                closedCount++
                super.close()
            }
        }
        val result = customWriter.write(output, plan)
        
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.LimitExceeded)
        assertThat(output.closedCount).isEqualTo(0)
        assertThat(attachmentSource.openCountMap[attUri]).isEqualTo(1)
        assertThat(attachmentSource.closeCountMap[attUri]).isEqualTo(1)
    }

    @Test
    fun `exact total limit succeeds`() = runTest {
        val snapshotB = "123".toByteArray()
        val prefsB = "456".toByteArray()
        val manifestB = "789".toByteArray()
        val checksumsMap = mapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to ImmutableBackupBytes.from(snapshotB).sha256(),
            BackupFormatV1Contract.MANIFEST_ENTRY to ImmutableBackupBytes.from(manifestB).sha256(),
            BackupFormatV1Contract.PREFERENCES_ENTRY to ImmutableBackupBytes.from(prefsB).sha256()
        )
        val checksumsB = "{\"data/database.json\":\"${checksumsMap[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${checksumsMap[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${checksumsMap[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()
        val total = (snapshotB.size + prefsB.size + manifestB.size + checksumsB.size).toLong()
        
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = total)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns ImmutableBackupBytes.from(snapshotB)
            every { preferencesJson } returns ImmutableBackupBytes.from(prefsB)
            every { manifestJson } returns ImmutableBackupBytes.from(manifestB)
            every { checksumsJson } returns ImmutableBackupBytes.from(checksumsB)
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
            val map = validPlan.expectedEntryChecksums.toMutableMap().apply {
                put(plannedAtt.archivePath, hash)
            }
            every { expectedEntryChecksums } returns map
            
            val corruptedJsonB = "{\"attachments/$attId/a.jpg\":\"${"0".repeat(64)}\",\"data/database.json\":\"${map[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${map[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${map[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()
            every { checksumsJson } returns ImmutableBackupBytes.from(corruptedJsonB)
            
            every { totalUncompressedBytes } returns (validPlan.totalUncompressedBytes + data.size + (corruptedJsonB.size - validPlan.checksumsJson.size))
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
            every { totalUncompressedBytes } returns (plan.totalUncompressedBytes + 100 + (checksumsJsonBytes.size - plan.checksumsJson.size))
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, mockedPlan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.AttachmentUnreadable)
    }

    @Test
    fun `writer detects attachment growth during write`() = runTest {
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val plannedData = "planned".toByteArray()
        val actualData = "planned and grown".toByteArray()
        
        attachmentSource.dataMap[attUri] = actualData

        val plannedAtt = PlannedBackupAttachment.create(
            attUri, attId, "attachments/$attId/a.jpg", "a.jpg", null, plannedData.size.toLong(), 
            ImmutableBackupBytes.from(plannedData).sha256(),
            listOf(BackupAttachmentReference("WASTE_EVENT", "w1"))
        )

        val validPlan = createValidMinimalPlan()
        val map = validPlan.expectedEntryChecksums.toMutableMap().apply {
            put(plannedAtt.archivePath, plannedAtt.checksumSha256)
        }
        val checksumsJsonBytes = "{\"attachments/$attId/a.jpg\":\"${plannedAtt.checksumSha256}\",\"data/database.json\":\"${map[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${map[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${map[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()

        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns validPlan.preferencesJson
            every { manifestJson } returns validPlan.manifestJson
            every { attachments } returns listOf(plannedAtt)
            every { expectedEntryChecksums } returns map
            every { checksumsJson } returns ImmutableBackupBytes.from(checksumsJsonBytes)
            // Use LARGE limit so it doesn't trigger LimitExceeded but DOES trigger AttachmentChanged
            every { totalUncompressedBytes } returns (validPlan.totalUncompressedBytes + plannedData.size + (checksumsJsonBytes.size - validPlan.checksumsJson.size))
        }
        
        val highLimits = BackupWriteLimits(maxTotalUncompressedBytes = 1000L)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, highLimits)

        val output = ByteArrayOutputStream()
        val result = customWriter.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.AttachmentChanged)
    }

    @Test
    fun `two writer instances with different limits remain isolated`() = runTest {
        val lowLimits = BackupWriteLimits(maxTotalUncompressedBytes = 10L)
        val highLimits = BackupWriteLimits(maxTotalUncompressedBytes = 1000L)
        
        val writer1 = DefaultBackupArchiveWriter(attachmentSource, lowLimits)
        val writer2 = DefaultBackupArchiveWriter(attachmentSource, highLimits)
        
        val plan = createValidMinimalPlan() // Size > 10
        
        assertThat(writer1.write(ByteArrayOutputStream(), plan)).isEqualTo(BackupArchiveWriteResult.Failure.LimitExceeded)
        assertThat(writer2.write(ByteArrayOutputStream(), plan)).isEqualTo(BackupArchiveWriteResult.Success)
    }

    @Test
    fun `checksum map mismatch rejected`() = runTest {
        val validPlan = createValidMinimalPlan()
        
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns validPlan.preferencesJson
            every { manifestJson } returns validPlan.manifestJson
            every { attachments } returns emptyList()
            every { totalUncompressedBytes } returns validPlan.totalUncompressedBytes
            
            val corruptedJson = "{\"data/database.json\":\"${"0".repeat(64)}\",\"manifest.json\":\"${validPlan.expectedEntryChecksums[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${validPlan.expectedEntryChecksums[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()
            every { checksumsJson } returns ImmutableBackupBytes.from(corruptedJson)
            every { expectedEntryChecksums } returns validPlan.expectedEntryChecksums
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.ChecksumInconsistency)
    }

    @Test
    fun `one byte above configured total fails during prevalidation`() = runTest {
        val plan = createValidMinimalPlan()
        val limits = BackupWriteLimits(maxTotalUncompressedBytes = plan.totalUncompressedBytes - 1)
        val customWriter = DefaultBackupArchiveWriter(attachmentSource, limits)
        
        val output = ByteArrayOutputStream()
        val result = customWriter.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.LimitExceeded)
    }
}
