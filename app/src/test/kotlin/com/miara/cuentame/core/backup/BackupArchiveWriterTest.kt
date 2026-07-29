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
        // Checksums map must be sorted by key to match expectations
        val sortedChecksums = checksumsMap.toSortedMap()
        val checksumsBytes = "{\"data/database.json\":\"$snapshotSha\",\"manifest.json\":\"$manifestSha\",\"preferences/settings.json\":\"$prefsSha\"}".toByteArray()

        return BackupPlan(
            snapshotDto = snapshot,
            snapshotJson = snapshotBytes,
            preferencesDto = prefs,
            preferencesJson = prefsBytes,
            attachments = emptyList(),
            manifest = manifest,
            manifestJson = manifestBytes,
            expectedEntryChecksums = sortedChecksums,
            checksumsJson = checksumsBytes,
            totalUncompressedBytes = 0L
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
        // Ownership: writer should NOT close caller's stream
        assertThat(output.closedCount).isEqualTo(0)
    }

    @Test
    fun `writer rejects plan with inconsistent checksums`() = runTest {
        val plan = createMinimalPlan()
        // Corrupt manifest bytes relative to its planned checksum
        val corruptedManifestBytes = ImmutableBackupBytes.from("corrupted".toByteArray())
        val corruptedPlan = plan.copy(manifestJson = corruptedManifestBytes)

        val output = ByteArrayOutputStream()
        val result = writer.write(output, corruptedPlan)
        assertThat(result).isInstanceOf(BackupArchiveWriteResult.Failure.IoError::class.java)
        val ioErr = result as BackupArchiveWriteResult.Failure.IoError
        assertThat(ioErr.cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(ioErr.cause.message).contains("checksum mismatch for manifest.json")
    }
}
