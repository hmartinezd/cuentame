package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
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
    private lateinit var installer: RestoreAttachmentInstaller

    @Before
    fun setup() {
        installer = RestoreAttachmentInstaller(storage)
    }

    @Test
    fun `captureRollback moves live to rollback`() {
        val liveDir = tempFolder.newFolder("live")
        val rollbackDir = tempFolder.newFolder("rollback")
        File(liveDir, "file.txt").writeText("data")
        
        every { storage.getLiveAttachmentDir() } returns liveDir
        every { storage.getRollbackDir("session") } returns rollbackDir
        
        installer.captureRollback("session")
        
        assertThat(liveDir.exists()).isFalse()
        assertThat(File(rollbackDir, "attachments/file.txt").exists()).isTrue()
    }

    @Test
    fun `installStaged moves staged to live`() {
        val liveDir = File(tempFolder.root, "live")
        val stagedDir = tempFolder.newFolder("staged")
        File(stagedDir, "new.txt").writeText("new")
        
        every { storage.getLiveAttachmentDir() } returns liveDir
        
        installer.installStaged("session", stagedDir)
        
        assertThat(stagedDir.exists()).isFalse()
        assertThat(File(liveDir, "new.txt").exists()).isTrue()
    }
}
