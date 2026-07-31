package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.backup.api.RestorePhase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class RestoreRecoveryCoordinatorTest {

    private val journal = mockk<RestoreJournal>()
    private val storage = mockk<InternalBackupRestoreStorage>()
    private val databaseApplier = mockk<RestoreDatabaseApplier>()
    private val attachmentInstaller = mockk<RestoreAttachmentInstaller>()
    private val preferencesApplier = mockk<RestorePreferencesApplier>()
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @Before
    fun setup() {
        coordinator = RestoreRecoveryCoordinator(
            journal, storage, databaseApplier, attachmentInstaller, preferencesApplier, codecs
        )
    }

    @Test
    fun `recoverIfNeeded does nothing when journal absent`() = runTest {
        every { journal.read() } returns null
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.NoRecoveryNeeded)
    }

    @Test
    fun `recoverIfNeeded cleans up session when phase is STAGING`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.STAGING, "hash", "staging", "rollback", null, 0)
        every { journal.read() } returns dto
        every { storage.cleanupSession("session") } just Runs
        every { journal.delete() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify { storage.cleanupSession("session") }
        verify { journal.delete() }
    }

    @Test
    fun `recoverIfNeeded performs rollback when mutation might have occurred`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.DATABASE_APPLIED, "hash", "staging", "rollback", null, 0)
        every { journal.read() } returns dto
        every { attachmentInstaller.rollback("session") } just Runs
        every { storage.getRollbackSnapshotFile("session") } returns File("missing") // skip DB rollback for simplicity in this test
        every { storage.cleanupSession("session") } just Runs
        every { journal.delete() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        coVerify { attachmentInstaller.rollback("session") }
    }
}
