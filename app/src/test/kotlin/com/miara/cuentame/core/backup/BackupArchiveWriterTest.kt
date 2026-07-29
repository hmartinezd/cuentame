package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.FakeBackupAttachmentSource
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import io.mockk.mockk

class BackupArchiveWriterTest {

    private val attachmentSource = FakeBackupAttachmentSource()
    private lateinit var writer: DefaultBackupArchiveWriter

    @Before
    fun setup() {
        writer = DefaultBackupArchiveWriter(attachmentSource)
    }

    private fun createMinimalPlan(): BackupPlan {
        return BackupPlan(
            snapshotDto = mockk(relaxed = true),
            snapshotJson = "{}".toByteArray(),
            preferencesDto = mockk(relaxed = true),
            preferencesJson = "{}".toByteArray(),
            attachments = emptyList(),
            manifest = mockk(relaxed = true),
            manifestJson = "{}".toByteArray(),
            expectedEntryChecksums = emptyMap(),
            checksumsJson = "{}".toByteArray(),
            totalUncompressedBytes = 0L
        )
    }

    @Test
    fun `exact entry order and deterministic content`() = runTest {
        val attUri = AttachmentSourceUri("uri1")
        val attData = "image data".toByteArray()
        attachmentSource.metadataMap[attUri] = AttachmentSourceMetadata(attUri, "pic.jpg", "image/jpeg")
        attachmentSource.dataMap[attUri] = attData
        
        val plannedChecksum = java.security.MessageDigest.getInstance("SHA-256").digest(attData).joinToString("") { "%02x".format(it) }
        
        val plannedAtt = PlannedBackupAttachment(
            sourceUri = attUri,
            attachmentId = "att1",
            archivePath = "attachments/att1/pic.jpg",
            displayName = "pic.jpg",
            mimeType = "image/jpeg",
            sizeBytes = attData.size.toLong(),
            checksumSha256 = plannedChecksum,
            references = emptyList()
        )

        val plan = createMinimalPlan().copy(
            snapshotJson = "db".toByteArray(),
            preferencesJson = "prefs".toByteArray(),
            manifestJson = "manifest".toByteArray(),
            checksumsJson = "checksums".toByteArray(),
            attachments = listOf(plannedAtt)
        )

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)

        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)

        val zis = ZipInputStream(ByteArrayInputStream(output.toByteArray()))
        val names = mutableListOf<String>()
        var entry = zis.nextEntry
        while (entry != null) {
            names.add(entry.name)
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertThat(names).containsExactly(
            "data/database.json",
            "preferences/settings.json",
            "attachments/att1/pic.jpg",
            "manifest.json",
            "checksums.json"
        ).inOrder()
    }

    @Test
    fun `failure if attachment changed after planning`() = runTest {
        val attUri = AttachmentSourceUri("uri1")
        attachmentSource.dataMap[attUri] = "original".toByteArray()
        
        val plannedAtt = PlannedBackupAttachment(
            sourceUri = attUri,
            attachmentId = "att1",
            archivePath = "attachments/att1/p.jpg",
            displayName = "p.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 8,
            checksumSha256 = "original-hash",
            references = emptyList()
        )

        val plan = createMinimalPlan().copy(attachments = listOf(plannedAtt))

        // Mutate content
        attachmentSource.dataMap[attUri] = "changed!!".toByteArray()

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)

        assertThat(result).isEqualTo(BackupArchiveWriteResult.Failure.AttachmentChanged)
    }
}
