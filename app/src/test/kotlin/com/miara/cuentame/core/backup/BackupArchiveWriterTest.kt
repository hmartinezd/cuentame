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

    private fun createValidMinimalPlan(): BackupPlan {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        val manifest = mockk<BackupManifest>(relaxed = true) {
            every { localeTag } returns "en-US"
            every { attachments } returns emptyList()
            every { tableMetadata } returns emptyMap()
            every { databaseSchemaVersion } returns 2
            every { backupFormatVersion } returns 1
        }
        
        val snapshotBytes = "{}".toByteArray()
        val prefsBytes = "{}".toByteArray()
        val manifestBytes = "{\"backupFormatVersion\":1,\"databaseSchemaVersion\":2}".toByteArray()
        
        val snapshotSha = ImmutableBackupBytes.from(snapshotBytes).sha256()
        val prefsSha = ImmutableBackupBytes.from(prefsBytes).sha256()
        val manifestSha = ImmutableBackupBytes.from(manifestBytes).sha256()

        val checksumsMap = mapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to snapshotSha,
            BackupFormatV1Contract.PREFERENCES_ENTRY to prefsSha,
            BackupFormatV1Contract.MANIFEST_ENTRY to manifestSha
        )
        // Checksums JSON must match exactly for pre-validation success
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
            totalUncompressedBytes = (snapshotBytes.size + prefsBytes.size + manifestBytes.size + checksumsBytes.size).toLong()
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
        
        if (result is BackupArchiveWriteResult.Failure.IoError) throw result.cause
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)
        assertThat(output.closedCount).isEqualTo(0)
    }

    @Test
    fun `writer rejects plan with checksum map mismatch`() = runTest {
        val validPlan = createValidMinimalPlan()
        // We use a mock to bypass BackupPlan.create() validation
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns validPlan.snapshotJson
            every { preferencesJson } returns validPlan.preferencesJson
            every { manifestJson } returns validPlan.manifestJson
            every { attachments } returns emptyList()
            every { totalUncompressedBytes } returns validPlan.totalUncompressedBytes
            
            // checksumsJson contents (decoded string) will NOT match expectedEntryChecksums map
            every { checksumsJson } returns ImmutableBackupBytes.from("{\"data/database.json\":\"${"0".repeat(64)}\",\"manifest.json\":\"${"0".repeat(64)}\",\"preferences/settings.json\":\"${"0".repeat(64)}\"}".toByteArray())
            every { expectedEntryChecksums } returns validPlan.expectedEntryChecksums
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.ChecksumInconsistency)
    }

    @Test
    fun `writer detects attachment growth during write`() = runTest {
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val plannedData = "planned".toByteArray()
        val actualData = "planned and grown".toByteArray()
        
        attachmentSource.dataMap[attUri] = actualData

        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val ref = BackupAttachmentReference("WASTE_EVENT", "w1")
        val plannedAtt = PlannedBackupAttachment.create(
            sourceUri = attUri,
            attachmentId = attId,
            archivePath = "attachments/$attId/a.jpg",
            displayName = "a.jpg",
            mimeType = "image/jpeg",
            sizeBytes = plannedData.size.toLong(),
            checksumSha256 = ImmutableBackupBytes.from(plannedData).sha256(),
            references = listOf(ref)
        )

        val manifest = mockk<BackupManifest>(relaxed = true) {
            every { localeTag } returns "en-US"
            every { databaseSchemaVersion } returns 2
            every { backupFormatVersion } returns 1
            every { attachments } returns listOf(mockk(relaxed = true) {
                every { attachmentId } returns attId
                every { archivePath } returns plannedAtt.archivePath
                every { displayName } returns plannedAtt.displayName
                every { mimeType } returns plannedAtt.mimeType
                every { sizeBytes } returns plannedAtt.sizeBytes
                every { checksumSha256 } returns plannedAtt.checksumSha256
                every { referencedBy } returns listOf(ref)
            })
        }

        val snapshotBytes = "{}".toByteArray()
        val prefsBytes = "{}".toByteArray()
        val manifestBytes = "{\"v\":1}".toByteArray()
        val checksumsMap = mapOf(
            BackupFormatV1Contract.DATABASE_ENTRY to ImmutableBackupBytes.from(snapshotBytes).sha256(),
            BackupFormatV1Contract.PREFERENCES_ENTRY to ImmutableBackupBytes.from(prefsBytes).sha256(),
            BackupFormatV1Contract.MANIFEST_ENTRY to ImmutableBackupBytes.from(manifestBytes).sha256(),
            plannedAtt.archivePath to plannedAtt.checksumSha256
        )
        
        val checksumsJson = "{\"attachments/$attId/a.jpg\":\"${plannedAtt.checksumSha256}\",\"data/database.json\":\"${checksumsMap[BackupFormatV1Contract.DATABASE_ENTRY]}\",\"manifest.json\":\"${checksumsMap[BackupFormatV1Contract.MANIFEST_ENTRY]}\",\"preferences/settings.json\":\"${checksumsMap[BackupFormatV1Contract.PREFERENCES_ENTRY]}\"}".toByteArray()

        val plan = BackupPlan.create(
            snapshotDto = snapshot,
            snapshotJson = snapshotBytes,
            preferencesDto = prefs,
            preferencesJson = prefsBytes,
            attachments = listOf(plannedAtt),
            manifest = manifest,
            manifestJson = manifestBytes,
            expectedEntryChecksums = checksumsMap,
            checksumsJson = checksumsJson,
            totalUncompressedBytes = (snapshotBytes.size + prefsBytes.size + manifestBytes.size + checksumsJson.size + plannedAtt.sizeBytes).toLong()
        )

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.AttachmentChanged)
    }
}
