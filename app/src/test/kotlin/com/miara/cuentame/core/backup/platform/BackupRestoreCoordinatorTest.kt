package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class BackupRestoreCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val reader = mockk<BackupArchiveReader>(relaxed = true)
    private val stager = mockk<BackupArchiveRestoreStager>(relaxed = true)
    private val databaseApplier = mockk<RestoreDatabaseApplier>(relaxed = true)
    private val attachmentInstaller = mockk<RestoreAttachmentInstaller>(relaxed = true)
    private val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true)
    private val journal = mockk<RestoreJournal>(relaxed = true)
    private val storage = mockk<InternalBackupRestoreStorage>(relaxed = true)
    private val recoveryCoordinator = mockk<RestoreRecoveryCoordinator>(relaxed = true)
    private val documentStore = mockk<BackupDocumentStore>(relaxed = true)
    private val codecs = BackupJsonCodecs()

    private lateinit var coordinator: BackupRestoreCoordinatorImpl

    @Before
    fun setup() {
        coordinator = BackupRestoreCoordinatorImpl(
            reader, stager, databaseApplier, attachmentInstaller, preferencesApplier,
            journal, storage, recoveryCoordinator, documentStore, codecs
        )
    }

    @Test
    fun `apply performs full sequence on success`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val input = mockk<InputStream>(relaxed = true)
        val stagingDir = tempFolder.newFolder("staging")
        
        val emptySnapshot = createEmptySnapshot()
        val stagingResult = BackupArchiveStagingResult.Success(
            snapshot = emptySnapshot,
            preferences = mockk(relaxed = true),
            manifest = mockk(relaxed = true),
            fingerprint = fingerprint,
            stagingDir = stagingDir
        )
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { documentStore.openForRead(source) } returns input
        coEvery { stager.stage(any(), any()) } returns stagingResult
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns emptySnapshot
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback.json")
        every { storage.getStagingDir(any()) } returns stagingDir
        every { storage.getRollbackDir(any()) } returns tempFolder.newFolder("rollback_dir")

        val result = coordinator.apply(source, fingerprint)
        
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    @Test
    fun `apply rolls back on mutation failure`() = runTest {
        val source = BackupDocumentUri("uri")
        val fingerprint = BackupArchiveFingerprint("hash")
        val input = mockk<InputStream>(relaxed = true)
        val stagingDir = tempFolder.newFolder("staging_fail")

        val emptySnapshot = createEmptySnapshot()
        val stagingResult = BackupArchiveStagingResult.Success(
            snapshot = emptySnapshot,
            preferences = mockk(relaxed = true),
            manifest = mockk(relaxed = true),
            fingerprint = fingerprint,
            stagingDir = stagingDir
        )
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { documentStore.openForRead(source) } returns input
        coEvery { stager.stage(any(), any()) } returns stagingResult
        
        coEvery { databaseApplier.captureRollbackSnapshot() } returns emptySnapshot
        coEvery { databaseApplier.replaceWith(emptySnapshot) } throws RuntimeException("DB Crash")
        
        every { storage.getRollbackSnapshotFile(any()) } returns tempFolder.newFile("rollback_fail.json")
        every { storage.getStagingDir(any()) } returns stagingDir
        every { storage.getRollbackDir(any()) } returns tempFolder.newFolder("rollback_dir_fail")
        
        val journalDto = RestoreJournalDto("session", RestorePhase.STAGED, "hash", "s", "r", null, 0)
        every { journal.read() } returns journalDto

        val result = coordinator.apply(source, fingerprint)
        
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
        val input = mockk<InputStream>(relaxed = true)
        val stagingResult = BackupArchiveStagingResult.Success(
            snapshot = createEmptySnapshot(),
            preferences = mockk(relaxed = true),
            manifest = mockk(relaxed = true),
            fingerprint = actualFingerprint,
            stagingDir = tempFolder.newFolder("staging_reject")
        )
        
        coEvery { recoveryCoordinator.recoverIfNeeded() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { documentStore.openForRead(source) } returns input
        coEvery { stager.stage(any(), any()) } returns stagingResult
        
        val result = coordinator.apply(source, expectedFingerprint)
        
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
