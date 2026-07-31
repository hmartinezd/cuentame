package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreCoordinatorImpl @Inject constructor(
    private val restoreRepository: BackupRestoreRepository,
    private val databaseApplier: RestoreDatabaseApplier,
    private val preferencesApplier: RestorePreferencesApplier,
    private val journal: RestoreJournal,
    private val storage: InternalBackupRestoreStorage,
    private val recoveryCoordinator: RestoreRecoveryCoordinator,
    private val codecs: BackupJsonCodecs
) : BackupRestoreCoordinator {

    private val operationLock = Mutex()

    override suspend fun inspect(source: BackupDocumentUri): BackupArchiveInspectionResult {
        return restoreRepository.inspect(source)
    }

    override suspend fun retryRecovery(): RestoreRecoveryResult = operationLock.withLock {
        recoveryCoordinator.retryRecovery()
    }

    override suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint,
        onProgress: suspend (BackupRestoreProgress) -> Unit
    ): BackupRestoreApplyResult = operationLock.withLock {
        // 1. Verify startup recovery is not required
        val recoveryCheck = recoveryCoordinator.recoverIfNeeded()
        if (recoveryCheck is RestoreRecoveryResult.RecoveryRequired) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
        }

        // 2. Reinspect source with production reader
        onProgress(BackupRestoreProgress.ValidatingBackup)
        val inspection = restoreRepository.inspect(source)
        val archive = when (inspection) {
            is BackupArchiveInspectionResult.Ready -> {
                if (inspection.eligibility is BackupRestoreEligibility.AttachmentsNotSupported) {
                    return BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
                }
                inspection.archive
            }
            is BackupArchiveInspectionResult.Failure -> return BackupRestoreApplyResult.Failure(inspection.reason)
        }

        // 3. Verify fingerprint
        if (archive.fingerprint != expectedFingerprint) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.InspectionExpired)
        }

        // 4. Verify backup has no attachments (double check)
        if (archive.manifest.attachments.isNotEmpty() || archive.snapshot.purchaseReceipts.any { it.attachmentId != null }) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
        }

        // 5. Verify current live data has no attachment references
        if (databaseApplier.hasExistingAttachmentReferences()) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
        }

        // 6. Validate incoming preferences
        try {
            com.miara.cuentame.core.preferences.model.ThemeMode.valueOf(archive.preferences.themeMode)
        } catch (e: Exception) {
            return BackupRestoreApplyResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        val sessionId = UUID.randomUUID().toString()
        var currentJournal = RestoreJournalDto(
            sessionId = sessionId,
            phase = RestorePhase.ROLLBACK_CAPTURED,
            expectedArchiveFingerprint = expectedFingerprint.value,
            startedAt = System.currentTimeMillis()
        )

        try {
            onProgress(BackupRestoreProgress.PreparingRollback)
            // 7. Capture rollback
            val rollback = databaseApplier.captureRollbackSnapshot()
            val prevPrefs = preferencesApplier.captureRollback()

            // 8. Persist rollback snapshot atomically
            storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString(rollback))

            // 9. Write journal ROLLBACK_CAPTURED
            journal.write(currentJournal)

            // 10. Mutation Boundary
            currentJournal = currentJournal.copy(phase = RestorePhase.MUTATION_STARTED)
            journal.write(currentJournal)

            withContext(NonCancellable) {
                try {
                    // 11. Replace Room
                    onProgress(BackupRestoreProgress.RestoringData)
                    databaseApplier.replaceWithBackup(archive.snapshot)
                    
                    currentJournal = currentJournal.copy(phase = RestorePhase.DATABASE_APPLIED)
                    journal.write(currentJournal)

                    // 12. Apply Preferences
                    onProgress(BackupRestoreProgress.RestoringSettings)
                    preferencesApplier.apply(archive.preferences)
                    
                    // 13. Verify preferences
                    val verifiedPrefs = preferencesApplier.captureRollback()
                    if (verifiedPrefs != archive.preferences) {
                        throw IllegalStateException("Preferences verification failed")
                    }

                    currentJournal = currentJournal.copy(phase = RestorePhase.PREFERENCES_APPLIED)
                    journal.write(currentJournal)

                    // 14. Finalizing
                    onProgress(BackupRestoreProgress.Finalizing)
                    if (!databaseApplier.verifyMatchesBackup(archive.snapshot)) {
                        throw IllegalStateException("Final database verification failed")
                    }

                    currentJournal = currentJournal.copy(phase = RestorePhase.COMPLETED)
                    journal.write(currentJournal)

                    // 15. Cleanup
                    storage.cleanupSession(sessionId)
                    journal.delete()
                } catch (e: Exception) {
                    // Failure after mutation began -> Rollback
                    onProgress(BackupRestoreProgress.RollingBack)
                    currentJournal = currentJournal.copy(phase = RestorePhase.ROLLING_BACK)
                    journal.write(currentJournal)

                    try {
                        databaseApplier.restoreRollback(rollback)
                        preferencesApplier.apply(prevPrefs)
                        
                        if (!databaseApplier.verifyMatchesRollback(rollback)) {
                            throw IllegalStateException("Rollback verification failed")
                        }
                        
                        currentJournal = currentJournal.copy(phase = RestorePhase.ROLLBACK_COMPLETED)
                        journal.write(currentJournal)
                        
                        storage.cleanupSession(sessionId)
                        journal.delete()
                    } catch (rollbackError: Exception) {
                        journal.write(currentJournal.copy(phase = RestorePhase.RECOVERY_REQUIRED))
                        throw rollbackError
                    }
                    throw e
                }
            }

            return BackupRestoreApplyResult.Success

        } catch (e: Exception) {
            val journalResult = journal.read()
            if (journalResult is RestoreJournalReadResult.Present && journalResult.journal.phase == RestorePhase.RECOVERY_REQUIRED) {
                return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
            }
            
            // If mutation didn't start or rollback succeeded, return meaningful failure
            if (currentJournal.phase == RestorePhase.ROLLBACK_CAPTURED || currentJournal.phase == RestorePhase.MUTATION_STARTED) {
                 storage.cleanupSession(sessionId)
                 journal.delete()
            }
            
            return BackupRestoreApplyResult.Failure(mapException(e))
        }
    }

    private fun mapException(e: Exception): BackupRestoreFailure {
        return when (e) {
            is IllegalStateException -> {
                if (e.message?.contains("Database") == true) BackupRestoreFailure.DatabaseRestoreFailed
                else if (e.message?.contains("Preferences") == true) BackupRestoreFailure.PreferencesRestoreFailed
                else BackupRestoreFailure.GenericIo
            }
            else -> BackupRestoreFailure.GenericIo
        }
    }
}
