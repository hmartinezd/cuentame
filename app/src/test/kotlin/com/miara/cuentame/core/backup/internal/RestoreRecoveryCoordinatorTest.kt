package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.backup.api.RestorePhase
import com.miara.cuentame.core.backup.api.RestoreRecoveryResult
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.encodeToString
import java.io.File

class RestoreRecoveryCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val journal = mockk<RestoreJournal>()
    private val storage = mockk<InternalBackupRestoreStorage>()
    private val databaseApplier = mockk<RestoreDatabaseApplier>(relaxed = true)
    private val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true)
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @Before
    fun setup() {
        coordinator = RestoreRecoveryCoordinator(
            journal, storage, databaseApplier, preferencesApplier, codecs
        )
        every { preferencesApplier.validate(any()) } returns true
        every { journal.write(any()) } just Runs
        every { journal.deleteOrThrow() } just Runs
        every { storage.cleanupSessionOrThrow(any()) } just Runs
    }


    @Test
    fun `recoverIfNeeded does nothing when journal absent`() = runTest {
        every { journal.read() } returns RestoreJournalReadResult.Absent
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.NoRecoveryNeeded)
    }

    @Test
    fun `recoverIfNeeded cleans up session when mutation not started`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.ROLLBACK_CAPTURED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify { storage.cleanupSessionOrThrow("session") }
        verify { journal.deleteOrThrow() }
    }

    @Test
    fun `recoverIfNeeded performs rollback when mutation occurred`() = runTest {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val dto = RestoreJournalDto("session", RestorePhase.DATABASE_APPLIED, "hash", prefs, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        
        val rollbackSnapshot = setupRollbackFile("session")
        
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { preferencesApplier.captureRollback() } returns prefs
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.write(any()) } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        coVerify { databaseApplier.restoreRollback(match { it.snapshot == rollbackSnapshot.snapshot }) }
        coVerify { preferencesApplier.apply(prefs) }
    }

    @Test
    fun `recovery handles ROLLING_BACK phase correctly`() = runTest {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val dto = RestoreJournalDto("session", RestorePhase.ROLLING_BACK, "hash", prefs, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        
        setupRollbackFile("session")
        
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { preferencesApplier.captureRollback() } returns prefs
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.write(any()) } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        // Should NOT write ROLLING_BACK again if already there
        verify(exactly = 0) { journal.write(match { it.phase == RestorePhase.ROLLING_BACK }) }
        verify(exactly = 1) { journal.write(match { it.phase == RestorePhase.ROLLBACK_COMPLETED }) }
    }


    @Test
    fun `retry from COMPLETED performs cleanup only`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.retryRecovery()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify { storage.cleanupSessionOrThrow("session") }
        verify { journal.deleteOrThrow() }
        coVerify(exactly = 0) { databaseApplier.restoreRollback(any()) }
    }

    @Test
    fun `durable COMPLETED cleanup failure retries cleanup without rollback`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.retryRecovery()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify { storage.cleanupSessionOrThrow("session") }
        verify { journal.deleteOrThrow() }
        coVerify(exactly = 0) { databaseApplier.restoreRollback(any()) }
    }

    @Test
    fun `recovery requires recovery when preference verification fails`() = runTest {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val dto = RestoreJournalDto("session", RestorePhase.DATABASE_APPLIED, "hash", prefs, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        
        setupRollbackFile("session")
        
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { preferencesApplier.captureRollback() } returns prefs.copy(themeMode = "LIGHT")
        
        every { journal.write(any()) } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.VERIFICATION_FAILED)
        assertThat(recovery.rollbackVerificationFailed).isTrue()
    }


    @Test
    fun `recovery is idempotent`() = runTest {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val dto = RestoreJournalDto("session", RestorePhase.DATABASE_APPLIED, "hash", prefs, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        setupRollbackFile("session")
        
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { preferencesApplier.captureRollback() } returns prefs
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.write(any()) } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        // First run
        coordinator.recoverIfNeeded()
        
        // Second run (journal should be absent now)
        every { journal.read() } returns RestoreJournalReadResult.Absent
        val result2 = coordinator.recoverIfNeeded()
        
        assertThat(result2).isEqualTo(RestoreRecoveryResult.NoRecoveryNeeded)
    }

    @Test
    fun `corrupt journal results in RecoveryRequired`() = runTest {
        every { journal.read() } returns RestoreJournalReadResult.Corrupt
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.JOURNAL_CORRUPT)
    }

    @Test
    fun `missing rollback snapshot after mutation results in RecoveryRequired`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.MUTATION_STARTED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.getRollbackSnapshotFile("session") } returns File(tempFolder.root, "missing")
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.SNAPSHOT_MISSING)
    }

    @Test
    fun `recovery fails if database rollback fails`() = runTest {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val dto = RestoreJournalDto("session", RestorePhase.DATABASE_APPLIED, "hash", prefs, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        setupRollbackFile("session")
        
        coEvery { databaseApplier.restoreRollback(any()) } throws RuntimeException("DB Restore Fail")

        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.DATABASE_RESTORE_FAILED)
        assertThat(recovery.databaseReplacementBegan).isTrue()
    }

    @Test
    fun `cleanup failure from COMPLETED returns RecoveryRequired`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } throws java.io.IOException("Cleanup failed")
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.CLEANUP_FAILED)
    }


    @Test
    fun `ROLLBACK_CAPTURED cleanup failure preserves journal`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.ROLLBACK_CAPTURED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } throws java.io.IOException("Cleanup failed")
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.CLEANUP_FAILED)
        verify(exactly = 0) { journal.deleteOrThrow() }
    }

    @Test
    fun `ROLLBACK_COMPLETED cleanup failure preserves journal`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.ROLLBACK_COMPLETED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } throws java.io.IOException("Cleanup failed")
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.CLEANUP_FAILED)
        verify(exactly = 0) { journal.deleteOrThrow() }
    }

    @Test
    fun `COMPLETED journal deletion failure remains retryable`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.deleteOrThrow() } throws java.io.IOException("Delete failed")
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.category).isEqualTo(com.miara.cuentame.core.backup.api.RecoveryFailureCategory.CLEANUP_FAILED)
        // Journal was NOT deleted, so it's still Present for next retry
    }


    @Test
    fun `retry succeeds after cleanup failure becomes available`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, 0)
        
        // First try fails
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.cleanupSessionOrThrow("session") } throws java.io.IOException("Still locked")
        coordinator.retryRecovery()
        
        // Second try succeeds
        every { storage.cleanupSessionOrThrow("session") } just Runs
        every { journal.deleteOrThrow() } just Runs
        
        val result = coordinator.retryRecovery()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify(exactly = 1) { journal.deleteOrThrow() }
    }

    @Test
    fun `generic RECOVERY_REQUIRED preserves evidence`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.RECOVERY_REQUIRED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        val recovery = result as RestoreRecoveryResult.RecoveryRequired
        assertThat(recovery.phase).isEqualTo(RestorePhase.RECOVERY_REQUIRED)
        verify(exactly = 0) {
            storage.cleanupSessionOrThrow(any())
            journal.deleteOrThrow()
            journal.write(any())
        }
    }


    @Test
    fun `validate returns true for valid theme and locale`() {
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        assertThat(preferencesApplier.validate(prefs)).isTrue()
    }

    private fun setupRollbackFile(sessionId: String): RestoreDatabaseRollbackSnapshot {
        val rollbackFile = tempFolder.newFile("rollback_$sessionId.json")
        val rollbackSnapshot = RestoreDatabaseRollbackSnapshot(
            snapshot = createMinimalSnapshot(),
            purchaseReceiptAttachmentPaths = emptyMap(),
            wasteEventAttachmentPaths = emptyMap()
        )
        rollbackFile.writeText(codecs.writer.encodeToString(rollbackSnapshot))
        every { storage.getRollbackSnapshotFile(sessionId) } returns rollbackFile
        return rollbackSnapshot
    }

    private fun createMinimalSnapshot() = com.miara.cuentame.core.backup.model.BackupSnapshotDto(
        restaurants = emptyList(),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )
}
