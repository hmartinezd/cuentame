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
    private val stager = mockk<BackupArchiveRestoreStager>(relaxed = true)
    private val attachmentInstaller = mockk<RestoreAttachmentInstaller>(relaxed = true)
    private val backupDocumentStore = mockk<BackupDocumentStore>(relaxed = true)
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: BackupRestoreCoordinatorImpl

    @Before
    fun setup() {
        operationGate.updateRecoveryState(RestoreStartupState.Ready)
        coordinator = BackupRestoreCoordinatorImpl(
            restoreRepository, databaseApplier, preferencesApplier,
            journal, storage, recoveryCoordinator, operationGate, 
            stager, attachmentInstaller, backupDocumentStore, codecs,
            NoOpRestoreFailureInjector()
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
        
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        coEvery { databaseApplier.hasExistingAttachmentReferences() } returns false
        coEvery { preferencesApplier.captureRollback() } returns archive.preferences
        coEvery { preferencesApplier.validate(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        coEvery { databaseApplier.verifyMatchesBackup(any(), any()) } returns true
        
        val rollbackFile = tempFolder.newFile("rollback_success.json")
        every { storage.getRollbackSnapshotFile(any()) } returns rollbackFile
        
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging"))

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    @Test
    fun `failed rollback returns RecoveryRequired`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")

        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        coEvery { preferencesApplier.validate(any()) } returns true
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_fail"))

        coEvery { databaseApplier.replaceWithBackup(any(), any()) } throws RuntimeException("Initial Fail")
        coEvery { databaseApplier.restoreRollback(any()) } throws RuntimeException("Rollback Fail")
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_die_public.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired))
    }

    @Test
    fun `failed rollback publishes global RecoveryRequired`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")

        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        coEvery { preferencesApplier.validate(any()) } returns true
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_fail_global"))

        coEvery { databaseApplier.replaceWithBackup(any(), any()) } throws RuntimeException("Initial Fail")
        coEvery { databaseApplier.restoreRollback(any()) } throws RuntimeException("Rollback Fail")
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_die_global.json")

        coordinator.apply(source, fingerprint) {}
        
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.RecoveryRequired)
    }

    @Test
    fun `cleanup failure after successful mutation returns RecoveryRequired`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        val inspectionResult = BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { restoreRepository.inspect(source) } returns inspectionResult
        coEvery { preferencesApplier.validate(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        coEvery { databaseApplier.verifyMatchesBackup(any(), any()) } returns true
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        coEvery { preferencesApplier.captureRollback() } returns archive.preferences

        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_cleanup_fail"))

        every { storage.cleanupSessionOrThrow(any()) } throws java.io.IOException("Cleanup failed")
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_cleanup_fail_global.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.RecoveryRequired)
    }

    @Test
    fun `failed MUTATION_STARTED journal write returns RestorePreparationFailed`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { preferencesApplier.validate(any()) } returns true
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(createEmptySnapshot(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        
        // Fail mutation write
        every { journal.write(match { it.phase == RestorePhase.MUTATION_STARTED }) } throws RuntimeException("Disk full")
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("disk_full.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RestorePreparationFailed))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)
        
        coVerify(exactly = 0) {
            databaseApplier.replaceWithBackup(any(), any())
            databaseApplier.restoreRollback(any())
        }
    }

    @Test
    fun `failed MUTATION_STARTED journal write and cleanup failure requires recovery`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { preferencesApplier.validate(any()) } returns true
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns RestoreDatabaseRollbackSnapshot(createEmptySnapshot(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        
        // Fail mutation write
        every { journal.write(match { it.phase == RestorePhase.MUTATION_STARTED }) } throws RuntimeException("Disk full")
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("disk_full_cleanup_fail.json")
        
        // Fail cleanup - using exact sessionId matching or just ensuring it's the right mock
        // Since storage is relaxed, we must explicitly throw for this specific call
        every { storage.cleanupSessionOrThrow(any()) } throws java.io.IOException("Cleanup fail")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.RecoveryRequired)
    }

    @Test
    fun `successful rollback returns DatabaseRestoreFailed`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { preferencesApplier.validate(any()) } returns true
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        coEvery { preferencesApplier.captureRollback() } returns archive.preferences
        
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_rollback_ok"))

        coEvery { databaseApplier.replaceWithBackup(any(), any()) } throws RuntimeException("DB Crash")
        
        // Rollback succeeds
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_ok.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.DatabaseRestoreFailed))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)
    }

    @Test
    fun `apply rolls back on mutation failure`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val emptySnapshot = createEmptySnapshot()
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true) { every { themeMode } returns "SYSTEM" }
            every { this@mockk.manifest } returns mockk(relaxed = true) { every { attachments } returns emptyList() }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { preferencesApplier.validate(any()) } returns true
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        coEvery { preferencesApplier.captureRollback() } returns archive.preferences
        
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_rollback_mut"))

        coEvery { databaseApplier.replaceWithBackup(any(), any()) } throws RuntimeException("DB Crash")
        
        // Rollback succeeds
        coEvery { databaseApplier.restoreRollback(any()) } just Runs
        coEvery { preferencesApplier.apply(any()) } just Runs
        coEvery { databaseApplier.verifyMatchesRollback(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_ok_hist.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.DatabaseRestoreFailed))
        assertThat(operationGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)
        
        coVerify {
            databaseApplier.restoreRollback(rollback)
            preferencesApplier.apply(any())
            databaseApplier.verifyMatchesRollback(rollback)
            preferencesApplier.verifyMatches(any())
        }
    }

    @Test
    fun `apply rejects changed archive fingerprint`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val differentFingerprint = BackupArchiveFingerprint("different")
        
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns differentFingerprint
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.InspectionExpired))
        
        coVerify(exactly = 0) {
            databaseApplier.captureRollbackSnapshot()
            journal.write(any())
            databaseApplier.replaceWithBackup(any(), any())
            preferencesApplier.apply(any())
        }
    }

    @Test
    fun `apply rejects invalid theme before mutation`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        
        val archive = mockk<InspectedBackupArchive>(relaxed = true) {
            every { this@mockk.fingerprint } returns fingerprint
            every { this@mockk.preferences } returns mockk(relaxed = true)
            every { this@mockk.manifest } returns mockk(relaxed = true) {
                every { attachments } returns emptyList()
            }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        coEvery { preferencesApplier.validate(any()) } returns false
        
        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Failure(BackupRestoreFailure.MalformedPreferences))
        
        coVerify(exactly = 0) {
            databaseApplier.captureRollbackSnapshot()
            journal.write(any())
            databaseApplier.replaceWithBackup(any(), any())
            preferencesApplier.apply(any())
        }
    }

    @Test
    fun `apply allows attachments eligibility before mutation`() = runTest {
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
                every { attachments } returns listOf(mockk(relaxed = true))
            }
        }
        coEvery { restoreRepository.inspect(source) } returns BackupArchiveInspectionResult.Ready(
            archive, mockk(relaxed = true), BackupRestoreEligibility.Eligible
        )
        coEvery { preferencesApplier.validate(any()) } returns true
        coEvery { preferencesApplier.verifyMatches(any()) } returns true
        coEvery { databaseApplier.verifyMatchesBackup(any(), any()) } returns true
        coEvery { backupDocumentStore.openForRead(any()) } returns "".byteInputStream()
        coEvery { stager.stage(any(), any()) } returns BackupArchiveStagingResult.Success(emptySnapshot, archive.preferences, archive.manifest, fingerprint, tempFolder.newFolder("staging_att_ok"))
        
        val rollback = RestoreDatabaseRollbackSnapshot(emptySnapshot, emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>(), emptyMap<String, String?>())
        coEvery { databaseApplier.captureRollbackSnapshot() } returns rollback
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_att.json")

        val result = coordinator.apply(source, fingerprint) {}
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    private fun createEmptySnapshot() = com.miara.cuentame.core.backup.model.BackupSnapshotDto(
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
