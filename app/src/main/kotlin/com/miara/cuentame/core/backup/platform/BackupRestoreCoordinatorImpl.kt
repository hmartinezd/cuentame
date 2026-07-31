package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreCoordinatorImpl @Inject constructor(
    private val reader: BackupArchiveReader,
    private val stager: BackupArchiveRestoreStager,
    private val databaseApplier: RestoreDatabaseApplier,
    private val attachmentInstaller: RestoreAttachmentInstaller,
    private val preferencesApplier: RestorePreferencesApplier,
    private val journal: RestoreJournal,
    private val storage: InternalBackupRestoreStorage,
    private val recoveryCoordinator: RestoreRecoveryCoordinator,
    private val documentStore: BackupDocumentStore,
    private val codecs: BackupJsonCodecs
) : BackupRestoreCoordinator {

    override suspend fun inspect(source: BackupDocumentUri): BackupArchiveInspectionResult {
        return try {
            documentStore.openForRead(source).use { input ->
                reader.inspect(input, source)
            }
        } catch (e: Exception) {
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.GenericIo)
        }
    }

    override suspend fun recoverIfNeeded(): RestoreRecoveryResult {
        return recoveryCoordinator.recoverIfNeeded()
    }

    override suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint
    ): BackupRestoreApplyResult {
        // 1. Verify no recovery-required state blocks restore
        val recoveryResult = recoverIfNeeded()
        if (recoveryResult is RestoreRecoveryResult.RecoveryRequired) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
        }

        // 2. Create restore session
        val sessionId = UUID.randomUUID().toString()
        var currentJournal = RestoreJournalDto(
            sessionId = sessionId,
            phase = RestorePhase.STAGING,
            expectedArchiveFingerprint = expectedFingerprint.value,
            stagingDirPath = storage.getStagingDir(sessionId).absolutePath,
            rollbackDirPath = storage.getRollbackDir(sessionId).absolutePath,
            startedAt = System.currentTimeMillis()
        )
        
        try {
            // 3. Write journal phase STAGING
            journal.write(currentJournal)

            // 4. Reopen source, Stream, validate and stage archive
            val stagingResult = documentStore.openForRead(source).use { input ->
                stager.stage(sessionId, input)
            }

            val staged = when (stagingResult) {
                is BackupArchiveStagingResult.Success -> stagingResult
                is BackupArchiveStagingResult.Failure -> return BackupRestoreApplyResult.Failure(stagingResult.reason)
            }

            // 6. Compare archive fingerprint
            if (staged.fingerprint != expectedFingerprint) {
                return BackupRestoreApplyResult.Failure(BackupRestoreFailure.InspectionExpired)
            }

            // 7. Write journal phase STAGED
            currentJournal = currentJournal.copy(phase = RestorePhase.STAGED)
            journal.write(currentJournal)

            // 8. Capture rollback
            val rollbackSnapshot = databaseApplier.captureRollbackSnapshot()
            val prevPrefs = preferencesApplier.captureRollback()
            
            // Save snapshot to disk
            storage.getRollbackSnapshotFile(sessionId).writeText(
                codecs.writer.encodeToString(rollbackSnapshot)
            )
            
            attachmentInstaller.captureRollback(sessionId)
            
            // 11. Write journal phase ROLLBACK_CAPTURED
            currentJournal = currentJournal.copy(
                phase = RestorePhase.ROLLBACK_CAPTURED,
                previousPreferences = prevPrefs
            )
            journal.write(currentJournal)

            // 12. Enter the non-interruptible mutation section
            withContext(NonCancellable) {
                try {
                    // 13. Apply database
                    databaseApplier.replaceWith(staged.snapshot)
                    
                    // 14. Write journal phase DATABASE_APPLIED
                    currentJournal = currentJournal.copy(phase = RestorePhase.DATABASE_APPLIED)
                    journal.write(currentJournal)
                    
                    // 15. Install attachments
                    attachmentInstaller.installStaged(sessionId, staged.stagingDir)
                    
                    // 16. Write journal phase ATTACHMENTS_APPLIED
                    currentJournal = currentJournal.copy(phase = RestorePhase.ATTACHMENTS_APPLIED)
                    journal.write(currentJournal)
                    
                    // 17. Apply preferences
                    preferencesApplier.apply(staged.preferences)
                    
                    // 18. Write journal phase PREFERENCES_APPLIED
                    currentJournal = currentJournal.copy(phase = RestorePhase.PREFERENCES_APPLIED)
                    journal.write(currentJournal)
                    
                    // 20. Write journal phase COMPLETED
                    currentJournal = currentJournal.copy(phase = RestorePhase.COMPLETED)
                    journal.write(currentJournal)
                    
                    // 21-23. Cleanup
                    storage.cleanupSession(sessionId)
                    journal.delete()
                } catch (e: Exception) {
                    // 24. Failure after mutation began -> Rollback
                    currentJournal = currentJournal.copy(phase = RestorePhase.ROLLING_BACK)
                    journal.write(currentJournal)
                    
                    try {
                        performRollback(currentJournal)
                        journal.delete()
                    } catch (rollbackError: Exception) {
                        journal.write(currentJournal.copy(phase = RestorePhase.RECOVERY_REQUIRED))
                    }
                    throw e
                }
            }

            return BackupRestoreApplyResult.Success

        } catch (e: Exception) {
            // Cleanup staging if failure before mutation began
            if (currentJournal.phase.ordinal < RestorePhase.DATABASE_APPLIED.ordinal) {
                storage.cleanupSession(sessionId)
                journal.delete()
                return BackupRestoreApplyResult.Failure(BackupRestoreFailure.GenericIo)
            }

            val finalDto = journal.read()
            if (finalDto?.phase == RestorePhase.RECOVERY_REQUIRED) {
                return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
            }

            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RollbackFailed)
        }
    }

    private suspend fun performRollback(dto: RestoreJournalDto) {
        dto.previousPreferences?.let { preferencesApplier.apply(it) }
        attachmentInstaller.rollback(dto.sessionId)
        
        val snapshotFile = storage.getRollbackSnapshotFile(dto.sessionId)
        if (snapshotFile.exists()) {
            val snapshot = codecs.reader.decodeFromString<com.miara.cuentame.core.backup.model.BackupSnapshotDto>(snapshotFile.readText())
            databaseApplier.replaceWith(snapshot)
        }
        
        storage.cleanupSession(dto.sessionId)
    }
}
