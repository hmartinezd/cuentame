package com.venkoi.restaurantops.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.ChecksumProvider
import com.venkoi.restaurantops.core.model.backup.BackupAttachmentMetadata
import com.venkoi.restaurantops.core.model.backup.BackupAttachmentReference
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import io.mockk.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreAttachmentInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val storage = mockk<InternalBackupRestoreStorage>()
    private val checksumProvider = mockk<ChecksumProvider>()
    private lateinit var installer: RestoreAttachmentInstaller

    @Before
    fun setup() {
        every { storage.getFilesDir() } returns tempFolder.root
        installer = RestoreAttachmentInstaller(storage, checksumProvider)
    }

    @Test
    fun `captureRollback copies live to rollback`() {
        val liveDir = tempFolder.newFolder("live")
        val rollbackDir = tempFolder.newFolder("rollback")
        File(liveDir, "file.txt").writeText("data")
        
        every { storage.getLiveAttachmentDir() } returns liveDir
        every { storage.getRollbackDir("session") } returns rollbackDir
        every { checksumProvider.calculateChecksum(any()) } returns "hash"
        
        installer.captureRollback("session")
        
        assertThat(liveDir.exists()).isTrue()
        assertThat(File(rollbackDir, "attachments/file.txt").exists()).isTrue()
    }

    @Test
    fun `installStaged installs based on manifest`() {
        val stagedDir = tempFolder.newFolder("staged")
        val attId = "att1"
        val attFile = File(stagedDir, "attachments/$attId/file.jpg").apply {
            parentFile.mkdirs()
            writeText("bytes")
        }
        
        val liveDir = tempFolder.newFolder("live")
        every { storage.getLiveAttachmentDir() } returns liveDir

        val manifest = createManifestWithAttachment(attId, "file.jpg", "p1")
        
        installer.installStaged("session", stagedDir, manifest)
        
        val expectedFile = File(tempFolder.root, "attachments/purchases/p1/file.jpg")
        assertThat(expectedFile.exists()).isTrue()
        assertThat(expectedFile.readText()).isEqualTo("bytes")
    }

    private fun createManifestWithAttachment(id: String, name: String, recordId: String): BackupManifest {
        return mockk<BackupManifest>().apply {
            every { attachments } returns listOf(
                BackupAttachmentMetadata(
                    attachmentId = id,
                    archivePath = "attachments/$id/$name",
                    displayName = name,
                    mimeType = "image/jpeg",
                    sizeBytes = 5,
                    checksumSha256 = "hash",
                    referencedBy = listOf(BackupAttachmentReference("PURCHASE_RECEIPT", recordId))
                )
            )
        }
    }
}
