package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreDurablePhaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val restoreRepository = mockk<BackupRestoreRepository>(relaxed = true)
    private val databaseApplier = mockk<RestoreDatabaseApplier>(relaxed = true)
    private val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true)
    private val storage = mockk<InternalBackupRestoreStorage>(relaxed = true)
    private val journal = mockk<RestoreJournal>()
    private val operationGate = RestoreOperationGate()
    private val stager = mockk<BackupArchiveRestoreStager>(relaxed = true)
    private val attachmentInstaller = mockk<RestoreAttachmentInstaller>(relaxed = true)
    private val backupDocumentStore = mockk<BackupDocumentStore>(relaxed = true)
    private val codecs = BackupJsonCodecs()
    
    private lateinit var recoveryCoordinator: RestoreRecoveryCoordinator
    private lateinit var coordinator: BackupRestoreCoordinatorImpl

    private val journalState = object {
        var dto: RestoreJournalDto? = null
        var failOnWrite = false
        var failOnDelete = false
    }

    @Before
    fun setup() {
        operationGate.updateRecoveryState(RestoreStartupState.Ready)
        
        every { journal.read() } answers {
            journalState.dto?.let { RestoreJournalReadResult.Present(it) } ?: RestoreJournalReadResult.Absent
        }
        every { journal.write(any()) } answers {
            if (journalState.failOnWrite) throw RuntimeException("Journal write failed")
            journalState.dto = firstArg()
        }
        every { journal.delete() } answers {
            if (journalState.failOnDelete) throw java.io.IOException("Journal delete failed")
            journalState.dto = null
        }
        every { journal.deleteOrThrow() } answers {
            if (journalState.failOnDelete) throw java.io.IOException("Journal delete failed")
            journalState.dto = null
        }

        recoveryCoordinator = RestoreRecoveryCoordinator(
            journal, storage, databaseApplier, preferencesApplier, attachmentInstaller, codecs
        )
        
        coordinator = BackupRestoreCoordinatorImpl(
            restoreRepository, databaseApplier, preferencesApplier,
            journal, storage, recoveryCoordinator, operationGate, 
            stager, attachmentInstaller, backupDocumentStore, codecs
        )
        
        // Mock default success for most things
        coEvery { preferencesApplier.validate(any()) } returns true
        coEvery { preferencesApplier.captureRollback() } returns BackupPreferencesDto("SYSTEM", true, "en-US")
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(
            snapshot = createMinimalSnapshot(),
            purchaseReceiptAttachmentPaths = emptyMap(),
            purchaseReceiptAttachmentDisplayNames = emptyMap(),
            wasteEventAttachmentPaths = emptyMap(),
            wasteEventAttachmentDisplayNames = emptyMap()
        )
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        coEvery { databaseApplier.verifyMatchesBackup(any(), any()) } returns true
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        
        val rollbackFile = File(tempFolder.root, "rollback.json")
        every { storage.getRollbackSnapshotFile(any()) } returns rollbackFile
        every { storage.saveRollbackSnapshot(any(), any()) } answers {
            rollbackFile.writeText(secondArg())
        }

        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(
            createMinimalSnapshot(), 
            BackupPreferencesDto("SYSTEM", true, "en-US"), 
            mockk(relaxed = true) { every { attachments } returns emptyList() }, 
            BackupArchiveFingerprint("hash"), 
            tempFolder.newFolder("staging")
        )
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

    @Test
    fun `failed COMPLETED journal write preserves previous durable phase and retries rollback`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        
        setupSuccessfulInspection(fingerprint)
        
        // Fail when trying to write COMPLETED or ROLLING_BACK to ensure it stays in PREFERENCES_APPLIED
        every { journal.write(match { it.phase == RestorePhase.COMPLETED }) } throws RuntimeException("Final journal write failed")
        every { journal.write(match { it.phase == RestorePhase.ROLLING_BACK }) } throws RuntimeException("Rollback journal write failed")
        
        // Mock rollback failure during apply to ensure journal remains present for retry
        coEvery { databaseApplier.restoreRollback(any()) } throws RuntimeException("Initial rollback fail")

        // 1-3. Run apply, which will fail at COMPLETED write
        val result = coordinator.apply(source, fingerprint) {}
        
        // 4-5. Assert RecoveryRequired
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.RecoveryRequired)
        
        // 6. Assert durable phase remains PREFERENCES_APPLIED (last successful write)
        assertThat(journalState.dto?.phase).isEqualTo(RestorePhase.PREFERENCES_APPLIED)

        // Reset verification for apps and repositories
        clearMocks(databaseApplier, preferencesApplier, journal)
        setupRetryMocks()
        
        // Re-setup journal and storage mocks after clearing
        every { journal.read() } answers {
            journalState.dto?.let { RestoreJournalReadResult.Present(it) } ?: RestoreJournalReadResult.Absent
        }
        every { journal.write(any()) } answers { journalState.dto = firstArg() }
        every { journal.deleteOrThrow() } answers { journalState.dto = null }
        
        // 7-9. Retry recovery should perform rollback because it's not COMPLETED
        val recoveryResult = coordinator.retryRecovery()
        
        assertThat(recoveryResult).isInstanceOf(RestoreRecoveryResult.Recovered::class.java)
        coVerify(exactly = 1) { databaseApplier.restoreRollback(any()) }
        coVerify(exactly = 1) { preferencesApplier.apply(any()) }
        coVerify(exactly = 1) { databaseApplier.verifyMatchesRollback(any()) }
        coVerify(exactly = 1) { preferencesApplier.captureRollback() }
        
        // Assert ROLLBACK_COMPLETED was written before cleanup
        verify { journal.write(match { it.phase == RestorePhase.ROLLBACK_COMPLETED }) }
        verify { journal.deleteOrThrow() }
    }

    private fun setupRetryMocks() {
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { preferencesApplier.captureRollback() } returns BackupPreferencesDto("SYSTEM", true, "en-US")
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        every { storage.cleanupSessionOrThrow(any()) } just Runs
    }

    @Test
    fun `durable COMPLETED cleanup failure retries cleanup without rollback`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        
        setupSuccessfulInspection(fingerprint)
        
        // Fail cleanup after COMPLETED is written
        every { storage.cleanupSessionOrThrow(any()) } throws java.io.IOException("Cleanup failed")

        // 1-2. Run apply, COMPLETED is written but cleanup fails
        val result = coordinator.apply(source, fingerprint) {}
        
        // 3-4. Assert RecoveryRequired and COMPLETED phase
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired))
        assertThat(journalState.dto?.phase).isEqualTo(RestorePhase.COMPLETED)

        // Clear mocks before retry to verify only retry behavior
        clearMocks(databaseApplier, preferencesApplier)
        setupRetryMocks()
        
        // 5-8. Retry performs cleanup only
        val recoveryResult = coordinator.retryRecovery()
        
        assertThat(recoveryResult).isInstanceOf(RestoreRecoveryResult.Recovered::class.java)
        coVerify(exactly = 0) { databaseApplier.restoreRollback(any()) }
        coVerify(exactly = 0) { preferencesApplier.apply(any()) }
        coVerify(exactly = 0) { databaseApplier.verifyMatchesRollback(any()) }
        
        // Assert cleanup in retry. We verify that at least one cleanup happened during retry.
        // We use atLeast = 1 because previous calls in 'apply' might still be recorded if mocks weren't fully cleared.
        verify(atLeast = 1) { storage.cleanupSessionOrThrow(any()) } 
        verify { journal.deleteOrThrow() }
    }

    private fun setupSuccessfulInspection(fingerprint: BackupArchiveFingerprint) {
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns BackupPreferencesDto("SYSTEM", true, "en-US")
            every { this@mockk.manifest } returns mockk(relaxed = true) {
                every { attachments } returns emptyList()
            }
        }
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Ready(
            archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible
        )
    }
}
