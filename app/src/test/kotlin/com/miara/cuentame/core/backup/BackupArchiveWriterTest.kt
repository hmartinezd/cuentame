package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupArchiveWriteResult
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.backup.api.BackupPlan
import com.miara.cuentame.core.backup.api.ImmutableBackupBytes
import com.miara.cuentame.core.backup.fakes.FakeBackupAttachmentSource
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
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
    fun `writer rejects plan with inconsistent checksums`() = runTest {
        // We use a mock to provide inconsistent data that eludes the factory check
        val plan = mockk<BackupPlan> {
            every { snapshotJson } returns ImmutableBackupBytes.from("{}".toByteArray())
            every { preferencesJson } returns ImmutableBackupBytes.from("{}".toByteArray())
            every { manifestJson } returns ImmutableBackupBytes.from("corrupted".toByteArray())
            every { attachments } returns emptyList()
            every { expectedEntryChecksums } returns mapOf(
                "data/database.json" to "0".repeat(64),
                "preferences/settings.json" to "0".repeat(64),
                "manifest.json" to "1".repeat(64) // mismatch
            )
            every { checksumsJson } returns ImmutableBackupBytes.from("{}".toByteArray())
            every { totalUncompressedBytes } returns 100L
        }

        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        
        // Should fail during prevalidatePlan (checksums JSON check) or writeEntry
        assertThat(result).isInstanceOf(BackupArchiveWriteResult.Failure::class.java)
    }
}
