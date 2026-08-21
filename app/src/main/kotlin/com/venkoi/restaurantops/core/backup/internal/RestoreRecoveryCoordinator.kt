package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.api.BackupJsonCodecs
import com.venkoi.restaurantops.core.backup.api.RecoveryFailureCategory
import com.venkoi.restaurantops.core.backup.api.RestorePhase
import com.venkoi.restaurantops.core.backup.api.RestoreRecoveryResult
import com.venkoi.restaurantops.core.model.backup.RestoreDatabaseRollbackSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryCoordinator @Inject constructor(
    private val journal: RestoreJournal,
    private val storage: InternalBackupRestoreStorage,
    private val databaseApplier: RestoreDatabaseApplier,
    private val preferencesApplier: RestorePreferencesApplier,
    private val attachmentInstaller: RestoreAttachmentInstaller,
    private val codecs: BackupJsonCodecs
) {
    suspend fun recoverIfNeeded(): RestoreRecoveryResult {
        val result = journal.read()
        
        return when (result) {
            RestoreJournalReadResult.Absent -> RestoreRecoveryResult.NoRecoveryNeeded
            RestoreJournalReadResult.Corrupt -> {
                RestoreRecoveryResult.RecoveryRequired(
                    sessionId = "unknown",
                    phase = RestorePhase.RECOVERY_REQUIRED,
                    category = RecoveryFailureCategory.JOURNAL_CORRUPT
                )
            }
            is RestoreJournalReadResult.Present -> {
                val dto = result.journal
                try {
                    handlePhase(dto)
                } catch (e: Exception) {
                    val category = when (e) {



                        is SnapshotMissingException -> RecoveryFailureCategory.SNAPSHOT_MISSING
                        is SnapshotCorruptException -> RecoveryFailureCategory.SNAPSHOT_CORRUPT
                        is PreferencesMissingException -> RecoveryFailureCategory.PREFERENCES_MISSING
                        is DatabaseRestoreException -> RecoveryFailureCategory.DATABASE_RESTORE_FAILED
                        is PreferencesRestoreException -> RecoveryFailureCategory.PREFERENCES_RESTORE_FAILED
                        is VerificationFailedException -> RecoveryFailureCategory.VERIFICATION_FAILED
                        is CleanupFailedException -> RecoveryFailureCategory.CLEANUP_FAILED
                        else -> RecoveryFailureCategory.UNKNOWN
                    }

                    RestoreRecoveryResult.RecoveryRequired(
                        sessionId = dto.sessionId,
                        phase = dto.phase,
                        category = category,
                        databaseReplacementBegan = e is DatabaseRestoreException || e is VerificationFailedException,
                        rollbackBegan = dto.phase == RestorePhase.ROLLING_BACK || e is DatabaseRestoreException,
                        rollbackVerificationFailed = e is VerificationFailedException,
                        cleanupFailed = e is CleanupFailedException
                    )
                }
            }
        }
    }

    private suspend fun handlePhase(dto: RestoreJournalDto): RestoreRecoveryResult {
        return when (dto.phase) {
            RestorePhase.ROLLBACK_CAPTURED -> {
                try {
                    storage.cleanupSessionOrThrow(dto.sessionId)
                    journal.deleteOrThrow()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                } catch (e: Exception) {
                    throw CleanupFailedException(e)
                }
            }
            RestorePhase.MUTATION_STARTED,
            RestorePhase.DATABASE_APPLIED,
            RestorePhase.PREFERENCES_APPLIED,
            RestorePhase.ROLLING_BACK -> {
                performFullRollback(dto)
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.ROLLBACK_COMPLETED -> {
                try {
                    storage.cleanupSessionOrThrow(dto.sessionId)
                    journal.deleteOrThrow()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                } catch (e: Exception) {
                    throw CleanupFailedException(e)
                }
            }
            RestorePhase.COMPLETED -> {
                try {
                    storage.cleanupSessionOrThrow(dto.sessionId)
                    journal.deleteOrThrow()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                } catch (e: Exception) {
                    throw CleanupFailedException(e)
                }
            }
            RestorePhase.RECOVERY_REQUIRED -> {
                RestoreRecoveryResult.RecoveryRequired(
                    sessionId = dto.sessionId,
                    phase = RestorePhase.RECOVERY_REQUIRED,
                    category = RecoveryFailureCategory.UNKNOWN
                )
            }
        }
    }

    private suspend fun performFullRollback(dto: RestoreJournalDto) {
        // 1. Read and validate rollback snapshot
        val snapshotFile = storage.getRollbackSnapshotFile(dto.sessionId)
        if (!snapshotFile.exists()) {
            throw SnapshotMissingException()
        }

        val rollback = try {
            codecs.reader.decodeFromString<RestoreDatabaseRollbackSnapshot>(snapshotFile.readText())
        } catch (e: Exception) {
            throw SnapshotCorruptException(e)
        }

        // 2. Require non-null previousPreferences
        val prevPrefs = dto.previousPreferences ?: throw PreferencesMissingException()

        // 3. Write or preserve ROLLING_BACK
        if (dto.phase != RestorePhase.ROLLING_BACK) {
            journal.write(dto.copy(phase = RestorePhase.ROLLING_BACK))
        }

        // 4. Restore database
        try {
            databaseApplier.restoreRollback(rollback)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw DatabaseRestoreException(e)
        }
        
        // 5. Restore preferences
        try {
            preferencesApplier.apply(prevPrefs)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw PreferencesRestoreException(e)
        }

        // 5b. Restore attachments
        try {
            attachmentInstaller.rollback(dto.sessionId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw AttachmentRestoreException(e)
        }

        // 6. Verify database
        if (!databaseApplier.verifyMatchesRollback(rollback)) {
            throw VerificationFailedException("Database verification failed")
        }

        // 7. Verify preferences
        val currentPrefs = preferencesApplier.captureRollback()
        if (currentPrefs != prevPrefs) {
            throw VerificationFailedException("Preferences verification failed")
        }

        // 7b. Verify attachments
        try {
            val inventory = dto.attachmentInventory.takeIf { it.isNotEmpty() }
                ?: rollback.attachmentInventory
            attachmentInstaller.verifyInventory(inventory)
        } catch (e: Exception) {
            throw VerificationFailedException("Attachment verification failed: ${e.message}")
        }

        // 8. Write ROLLBACK_COMPLETED
        journal.write(dto.copy(phase = RestorePhase.ROLLBACK_COMPLETED))

        // 9. Cleanup
        try {
            storage.cleanupSessionOrThrow(dto.sessionId)
        } catch (e: Exception) {
            throw CleanupFailedException(e)
        }

        // 10. Delete journal
        try {
            journal.deleteOrThrow()
        } catch (e: Exception) {
            throw CleanupFailedException(e)
        }
    }

    suspend fun retryRecovery(): RestoreRecoveryResult {
        return recoverIfNeeded()
    }
}

private class SnapshotMissingException : RuntimeException()
private class SnapshotCorruptException(cause: Throwable) : RuntimeException(cause)
private class PreferencesMissingException : RuntimeException()
private class DatabaseRestoreException(cause: Throwable) : RuntimeException(cause)
private class PreferencesRestoreException(cause: Throwable) : RuntimeException(cause)
private class AttachmentRestoreException(cause: Throwable) : RuntimeException(cause)
private class VerificationFailedException(message: String) : RuntimeException(message)
private class CleanupFailedException(cause: Throwable) : RuntimeException(cause)

