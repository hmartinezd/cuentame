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
    private val databaseApplier = mockk<RestoreDatabaseApplier>()
    private val preferencesApplier = mockk<RestorePreferencesApplier>()
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: RestoreRecoveryCoordinator

    @Before
    fun setup() {
        coordinator = RestoreRecoveryCoordinator(
            journal, storage, databaseApplier, preferencesApplier, codecs
        )
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
        every { storage.cleanupSession("session") } just Runs
        every { journal.delete() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        verify { storage.cleanupSession("session") }
        verify { journal.delete() }
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
        every { storage.cleanupSession("session") } just Runs
        every { journal.write(any()) } just Runs
        every { journal.delete() } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered("session"))
        coVerify { databaseApplier.restoreRollback(match { it.snapshot == rollbackSnapshot.snapshot }) }
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
        every { storage.cleanupSession("session") } just Runs
        every { journal.write(any()) } just Runs
        every { journal.delete() } just Runs
        
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
    }

    @Test
    fun `missing rollback snapshot after mutation results in RecoveryRequired`() = runTest {
        val dto = RestoreJournalDto("session", RestorePhase.MUTATION_STARTED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(dto)
        every { storage.getRollbackSnapshotFile("session") } returns File(tempFolder.root, "missing")
        
        every { journal.write(any()) } just Runs
        
        val result = coordinator.recoverIfNeeded()
        
        assertThat(result).isInstanceOf(RestoreRecoveryResult.RecoveryRequired::class.java)
        verify { journal.write(match { it.phase == RestorePhase.RECOVERY_REQUIRED }) }
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
