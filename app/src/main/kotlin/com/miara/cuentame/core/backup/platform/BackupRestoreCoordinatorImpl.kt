package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val operationGate: RestoreOperationGate,
    private val codecs: BackupJsonCodecs
) : BackupRestoreCoordinator {

    override val startupState: StateFlow<RestoreStartupState> = operationGate.recoveryState

    override suspend fun inspect(source: BackupDocumentUri): BackupArchiveInspectionResult {
        return restoreRepository.inspect(source)
    }

    override suspend fun retryRecovery(): RestoreRecoveryResult = operationGate.mutex.withLock {
        recoveryCoordinator.retryRecovery()
    }

    override suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint,
        onProgress: suspend (BackupRestoreProgress) -> Unit
    ): BackupRestoreApplyResult = operationGate.mutex.withLock {
        // 1. Verify startup recovery is terminal
        val startupState = operationGate.recoveryState.first {
            it is RestoreStartupState.Ready ||
            it is RestoreStartupState.Recovered ||
            it is RestoreStartupState.RecoveryRequired
        }
        if (startupState is RestoreStartupState.RecoveryRequired) {
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

        // 4. Verify backup has no attachments
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
            val rollback = try {
                databaseApplier.captureRollbackSnapshot()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            val prevPrefs = try {
                preferencesApplier.captureRollback()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            // 8. Persist rollback snapshot atomically
            try {
                storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString(rollback))
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            // 9. Write journal ROLLBACK_CAPTURED with previous preferences
            currentJournal = currentJournal.copy(previousPreferences = prevPrefs)
            try {
                journal.write(currentJournal)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            // 10. Mutation Boundary
            if (currentJournal.previousPreferences == null) {
                throw RestorePreparationException(IllegalStateException("Previous preferences not captured"))
            }
            currentJournal = currentJournal.copy(phase = RestorePhase.MUTATION_STARTED)
            try {
                journal.write(currentJournal)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            withContext(NonCancellable) {
                try {
                    // 11. Replace Room
                    onProgress(BackupRestoreProgress.RestoringData)
                    try {
                        databaseApplier.replaceWithBackup(archive.snapshot)
                    } catch (e: Exception) {
                        throw RestoreDatabaseApplicationException(e)
                    }
                    
                    currentJournal = currentJournal.copy(phase = RestorePhase.DATABASE_APPLIED)
                    journal.write(currentJournal)

                    // 12. Apply Preferences
                    onProgress(BackupRestoreProgress.RestoringSettings)
                    try {
                        preferencesApplier.apply(archive.preferences)
                    } catch (e: Exception) {
                        throw RestorePreferencesApplicationException(e)
                    }
                    
                    // 13. Verify preferences
                    val verifiedPrefs = preferencesApplier.captureRollback()
                    if (verifiedPrefs != archive.preferences) {
                        throw RestorePreferencesApplicationException(IllegalStateException("Preferences verification failed"))
                    }

                    currentJournal = currentJournal.copy(phase = RestorePhase.PREFERENCES_APPLIED)
                    journal.write(currentJournal)

                    // 14. Finalizing
                    onProgress(BackupRestoreProgress.Finalizing)
                    if (!databaseApplier.verifyMatchesBackup(archive.snapshot)) {
                        throw RestoreFinalVerificationException()
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

        } catch (e: CancellationException) {
            if (currentJournal.phase == RestorePhase.ROLLBACK_CAPTURED) {
                storage.cleanupSession(sessionId)
                journal.delete()
            }
            throw e
        } catch (e: Exception) {
            val journalResult = journal.read()
            if (journalResult is RestoreJournalReadResult.Present && journalResult.journal.phase == RestorePhase.RECOVERY_REQUIRED) {
                return BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
            }
            
            // If mutation didn't start or rollback succeeded, clean up session
            if (currentJournal.phase == RestorePhase.ROLLBACK_CAPTURED || currentJournal.phase == RestorePhase.ROLLBACK_COMPLETED) {
                 storage.cleanupSession(sessionId)
                 journal.delete()
            }
            
            return BackupRestoreApplyResult.Failure(mapException(e))
        }
    }

    private fun mapException(e: Exception): BackupRestoreFailure {
        return when (e) {
            is RestorePreparationException -> BackupRestoreFailure.RestorePreparationFailed
            is RestoreDatabaseApplicationException -> BackupRestoreFailure.DatabaseRestoreFailed
            is RestorePreferencesApplicationException -> BackupRestoreFailure.PreferencesRestoreFailed
            is RestoreFinalVerificationException -> BackupRestoreFailure.FinalVerificationFailed
            else -> BackupRestoreFailure.GenericIo
        }
    }

    private class RestoreDatabaseApplicationException(cause: Throwable) : Exception(cause)
    private class RestorePreferencesApplicationException(cause: Throwable) : Exception(cause)
    private class RestoreFinalVerificationException : Exception()
    private class RestorePreparationException(cause: Throwable) : Exception(cause)
}
