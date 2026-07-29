package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.FakeBackupAttachmentSource
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
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
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val prefs = BackupPreferencesDto("SYSTEM", true, "en-US")
        val manifest = mockk<BackupManifest>(relaxed = true) {
            io.mockk.every { localeTag } returns "en-US"
            io.mockk.every { attachments } returns emptyList()
            io.mockk.every { tableMetadata } returns emptyMap()
        }
        
        val snapshotBytes = "{}".toByteArray()
        val prefsBytes = "{}".toByteArray()
        val manifestBytes = "{\"backupFormatVersion\":1}".toByteArray()
        
        val snapshotSha = ImmutableBackupBytes.from(snapshotBytes).sha256()
        val prefsSha = ImmutableBackupBytes.from(prefsBytes).sha256()
        val manifestSha = ImmutableBackupBytes.from(manifestBytes).sha256()

        val checksumsMap = mutableMapOf(
            "data/database.json" to snapshotSha,
            "preferences/settings.json" to prefsSha,
            "manifest.json" to manifestSha
        )
        val sortedChecksums = checksumsMap.toSortedMap()
        // Correct sorted order in JSON string for dummy check
        val checksumsBytes = "{\"data/database.json\":\"$snapshotSha\",\"manifest.json\":\"$manifestSha\",\"preferences/settings.json\":\"$prefsSha\"}".toByteArray()

        return BackupPlan.create(
            snapshotDto = snapshot,
            snapshotJson = snapshotBytes,
            preferencesDto = prefs,
            preferencesJson = prefsBytes,
            attachments = emptyList(),
            manifest = manifest,
            manifestJson = manifestBytes,
            expectedEntryChecksums = sortedChecksums,
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
        
        val plan = createMinimalPlan()
        val result = writer.write(output, plan)
        
        if (result is BackupArchiveWriteResult.Failure.IoError) {
             throw result.cause
        }
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)
        assertThat(output.closedCount).isEqualTo(0)
    }

    @Test
    fun `writer rejects plan with inconsistent checksums`() = runTest {
        val plan = createMinimalPlan()
        val snapshotSha = plan.snapshotJson.sha256()
        val prefsSha = plan.preferencesJson.sha256()
        val manifestSha = plan.manifestJson.sha256()
        
        val corruptedManifestBytes = ImmutableBackupBytes.from("corrupted".toByteArray())
        
        // We must also update totalUncompressedBytes to avoid preflight total size limit mismatch 
        // if the writer checks that before entry writing.
        val corruptedPlan = plan.copy(
            manifestJson = corruptedManifestBytes,
            totalUncompressedBytes = plan.totalUncompressedBytes + ("corrupted".length - plan.manifestJson.size)
        )

        val output = ByteArrayOutputStream()
        val result = writer.write(output, corruptedPlan)
        assertThat(result).isInstanceOf(BackupArchiveWriteResult.Failure.IoError::class.java)
        val ioErr = result as BackupArchiveWriteResult.Failure.IoError
        assertThat(ioErr.cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(ioErr.cause.message).contains("checksum mismatch for manifest.json")
    }
}
