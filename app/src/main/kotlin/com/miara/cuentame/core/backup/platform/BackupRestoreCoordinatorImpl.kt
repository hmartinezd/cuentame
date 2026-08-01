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
        operationGate.updateRecoveryState(RestoreStartupState.Recovering)
        try {
            val result = recoveryCoordinator.retryRecovery()
            val terminalState = when (result) {
                RestoreRecoveryResult.NoRecoveryNeeded -> RestoreStartupState.Ready
                is RestoreRecoveryResult.Recovered -> RestoreStartupState.Recovered(result.sessionId)
                is RestoreRecoveryResult.RecoveryRequired -> RestoreStartupState.RecoveryRequired
            }
            operationGate.updateRecoveryState(terminalState)
            result
        } catch (e: Exception) {
            operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
            RestoreRecoveryResult.RecoveryRequired(
                sessionId = "unknown",
                phase = RestorePhase.RECOVERY_REQUIRED,
                category = RecoveryFailureCategory.UNKNOWN
            )
        }

    }

    override suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint,
        onProgress: suspend (BackupRestoreProgress) -> Unit
    ): BackupRestoreApplyResult = operationGate.withOperationalLock(
        onRecoveryRequired = {
            BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
        }
    ) {
        // 2. Reinspect source with production reader
        onProgress(BackupRestoreProgress.ValidatingBackup)
        val inspection = restoreRepository.inspect(source)
        val archive = when (inspection) {
            is BackupArchiveInspectionResult.Ready -> {
                if (inspection.eligibility is BackupRestoreEligibility.AttachmentsNotSupported) {
                    return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
                }
                inspection.archive
            }
            is BackupArchiveInspectionResult.Failure -> return@withOperationalLock BackupRestoreApplyResult.Failure(inspection.reason)
        }

        // 3. Verify fingerprint
        if (archive.fingerprint != expectedFingerprint) {
            return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.InspectionExpired)
        }

        // 4. Verify backup has no attachments
        if (archive.manifest.attachments.isNotEmpty() || 
            archive.snapshot.purchaseReceipts.any { it.attachmentId != null } ||
            archive.snapshot.wasteEvents.any { it.attachmentId != null }) {
            return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
        }

        // 5. Verify current live data has no attachment references
        if (databaseApplier.hasExistingAttachmentReferences()) {
            return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.AttachmentsNotSupported)
        }

        // 6. Validate incoming preferences
        if (!preferencesApplier.validate(archive.preferences)) {
            return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        val sessionId = UUID.randomUUID().toString()
        var currentJournal = RestoreJournalDto(
            sessionId = sessionId,
            phase = RestorePhase.ROLLBACK_CAPTURED,
            expectedArchiveFingerprint = expectedFingerprint.value,
            startedAt = System.currentTimeMillis()
        )

        var recoveryRequired = false

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
            val rollbackCapturedJournal = currentJournal.copy(previousPreferences = prevPrefs)
            try {
                journal.write(rollbackCapturedJournal)
                currentJournal = rollbackCapturedJournal
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { throw RestorePreparationException(e) }

            // 10. Mutation Boundary
            if (currentJournal.previousPreferences == null) {
                throw RestorePreparationException(IllegalStateException("Previous preferences not captured"))
            }
            val mutationStartedJournal = currentJournal.copy(phase = RestorePhase.MUTATION_STARTED)
            try {
                journal.write(mutationStartedJournal)
                currentJournal = mutationStartedJournal
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
                    
                    val dbAppliedJournal = currentJournal.copy(phase = RestorePhase.DATABASE_APPLIED)
                    journal.write(dbAppliedJournal)
                    currentJournal = dbAppliedJournal

                    // 12. Apply Preferences
                    onProgress(BackupRestoreProgress.RestoringSettings)
                    try {
                        preferencesApplier.apply(archive.preferences)
                    } catch (e: Exception) {
                        throw RestorePreferencesApplicationException(e)
                    }
                    
                    // 13. Verify preferences
                    if (!preferencesApplier.verifyMatches(archive.preferences)) {
                        throw RestorePreferencesApplicationException(IllegalStateException("Preferences verification failed"))
                    }

                    val prefsAppliedJournal = currentJournal.copy(phase = RestorePhase.PREFERENCES_APPLIED)
                    journal.write(prefsAppliedJournal)
                    currentJournal = prefsAppliedJournal

                    // 14. Finalizing
                    onProgress(BackupRestoreProgress.Finalizing)
                    if (!databaseApplier.verifyMatchesBackup(archive.snapshot)) {
                        throw RestoreFinalVerificationException()
                    }

                    val completedJournal = currentJournal.copy(phase = RestorePhase.COMPLETED)
                    journal.write(completedJournal)
                    currentJournal = completedJournal

                    // 15. Cleanup
                    storage.cleanupSessionOrThrow(sessionId)
                    journal.deleteOrThrow()
                } catch (e: Exception) {
                    if (currentJournal.phase == RestorePhase.COMPLETED) {
                         // Successfully applied but failed to clean up.
                         recoveryRequired = true
                         throw e
                    }

                    // Failure after mutation began -> Rollback
                    onProgress(BackupRestoreProgress.RollingBack)
                    val rollingBackJournal = currentJournal.copy(phase = RestorePhase.ROLLING_BACK)
                    try {
                        journal.write(rollingBackJournal)
                        currentJournal = rollingBackJournal
                    } catch (ignore: Exception) {}

                    try {
                        databaseApplier.restoreRollback(rollback)
                        preferencesApplier.apply(prevPrefs)
                        
                        if (!databaseApplier.verifyMatchesRollback(rollback)) {
                            throw IllegalStateException("Rollback verification failed")
                        }
                        
                        if (!preferencesApplier.verifyMatches(prevPrefs)) {
                            throw IllegalStateException("Rollback preference verification failed")
                        }

                        val rollbackCompletedJournal = currentJournal.copy(phase = RestorePhase.ROLLBACK_COMPLETED)
                        journal.write(rollbackCompletedJournal)
                        currentJournal = rollbackCompletedJournal
                        
                        storage.cleanupSessionOrThrow(sessionId)
                        journal.deleteOrThrow()
                    } catch (rollbackError: Exception) {
                        recoveryRequired = true
                        throw rollbackError
                    }
                    throw e
                }
            }

            return@withOperationalLock BackupRestoreApplyResult.Success

        } catch (e: CancellationException) {
            if (currentJournal.phase == RestorePhase.ROLLBACK_CAPTURED) {
                try {
                    storage.cleanupSessionOrThrow(sessionId)
                    journal.deleteOrThrow()
                } catch (ignore: Exception) {}
            }
            throw e
        } catch (e: Exception) {
            if (recoveryRequired) {
                operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
                return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
            }
            
            // If mutation didn't start or rollback succeeded, clean up session
            if (currentJournal.phase == RestorePhase.ROLLBACK_CAPTURED || currentJournal.phase == RestorePhase.ROLLBACK_COMPLETED) {
                 try {
                     storage.cleanupSessionOrThrow(sessionId)
                     journal.deleteOrThrow()
                     return@withOperationalLock BackupRestoreApplyResult.Failure(mapException(e))
                 } catch (cleanupError: Exception) {
                     // Cleanup failure after preparation error escalates to RecoveryRequired
                 }
            }
            
            operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
            return@withOperationalLock BackupRestoreApplyResult.Failure(BackupRestoreFailure.RecoveryRequired)
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
