package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRestoreCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val restoreRepository = mockk<BackupRestoreRepository>(relaxed = true)
    private val databaseApplier = mockk<RestoreDatabaseApplier>(relaxed = true)
    private val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true)
    private val journal = mockk<RestoreJournal>(relaxed = true)
    private val storage = mockk<InternalBackupRestoreStorage>(relaxed = true)
    private val recoveryCoordinator = mockk<RestoreRecoveryCoordinator>(relaxed = true)
    private val operationGate = com.miara.cuentame.core.backup.internal.RestoreOperationGate()
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: BackupRestoreCoordinatorImpl

    @Before
    fun setup() {
        operationGate.updateRecoveryState(RestoreStartupState.Ready)
        coordinator = BackupRestoreCoordinatorImpl(
            restoreRepository, databaseApplier, preferencesApplier,
            journal, storage, recoveryCoordinator, operationGate, codecs
        )
    }

    @Test
    fun `apply performs full sequence on success`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        
        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.snapshot } returns emptySnapshot
            every { this@mockk.preferences } returns mockk(relaxed = true) {
                every { themeMode } returns "SYSTEM"
            }
            every { this@mockk.manifest } returns mockk(relaxed = true) {
                every { attachments } returns emptyList()
            }
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap(), emptyMap())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        coEvery { databaseApplier.hasExistingAttachmentReferences() } returns false
        coEvery { preferencesApplier.captureRollback() } returns archive.preferences
        coEvery { databaseApplier.verifyMatchesBackup(any()) } returns true
        
        val rollbackFile = tempFolder.newFile("rollback_success.json")
        every { storage.getRollbackSnapshotFile(any()) } returns rollbackFile

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    @Test
    fun `apply rolls back on mutation failure`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")

        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.snapshot } returns emptySnapshot
            every { this@mockk.preferences } returns mockk(relaxed = true) {
                every { themeMode } returns "SYSTEM"
            }
            every { this@mockk.manifest } returns mockk(relaxed = true) {
                every { attachments } returns emptyList()
            }
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap(), emptyMap())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        coEvery { databaseApplier.replaceWithBackup(any()) } throws RuntimeException("DB Crash")
        
        val rollbackFile = tempFolder.newFile("rollback_fail_trigger.json")
        every { storage.getRollbackSnapshotFile(any()) } returns rollbackFile
        
        val journalDto = RestoreJournalDto("session", RestorePhase.MUTATION_STARTED, "hash", null, 0)
        every { journal.read() } returns RestoreJournalReadResult.Present(journalDto)

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
        
        verify {
            journal.write(match { it.phase == RestorePhase.ROLLING_BACK })
        }
    }

    @Test
    fun `apply rejects changed archive fingerprint`() = runTest {
        val source = BackupDocumentUri("uri")
        val expectedFingerprint = BackupArchiveFingerprint("expected")
        val actualFingerprint = BackupArchiveFingerprint("actual")
        
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { fingerprint } returns actualFingerprint
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        
        val result = coordinator.apply(source, expectedFingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.InspectionExpired))
    }

    private fun createEmptySnapshot() = BackupSnapshotDto(
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
