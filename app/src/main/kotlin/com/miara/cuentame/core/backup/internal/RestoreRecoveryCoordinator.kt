package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.backup.api.RestorePhase
import com.miara.cuentame.core.backup.api.RestoreRecoveryResult
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryCoordinator @Inject constructor(
    private val journal: RestoreJournal,
    private val storage: InternalBackupRestoreStorage,
    private val databaseApplier: RestoreDatabaseApplier,
    private val preferencesApplier: RestorePreferencesApplier,
    private val codecs: BackupJsonCodecs
) {
    suspend fun recoverIfNeeded(): RestoreRecoveryResult {
        val result = journal.read()
        
        return when (result) {
            RestoreJournalReadResult.Absent -> RestoreRecoveryResult.NoRecoveryNeeded
            RestoreJournalReadResult.Corrupt -> {
                RestoreRecoveryResult.RecoveryRequired("unknown")
            }
            is RestoreJournalReadResult.Present -> {
                val dto = result.journal
                try {
                    handlePhase(dto)
                } catch (e: Exception) {
                    try {
                        journal.write(dto.copy(phase = RestorePhase.RECOVERY_REQUIRED))
                    } catch (ignore: Exception) {}
                    RestoreRecoveryResult.RecoveryRequired(dto.sessionId)
                }
            }
        }
    }

    private suspend fun handlePhase(dto: RestoreJournalDto): RestoreRecoveryResult {
        return when (dto.phase) {
            RestorePhase.ROLLBACK_CAPTURED -> {
                storage.cleanupSession(dto.sessionId)
                journal.delete()
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.MUTATION_STARTED,
            RestorePhase.DATABASE_APPLIED,
            RestorePhase.PREFERENCES_APPLIED,
            RestorePhase.ROLLING_BACK -> {
                performFullRollback(dto)
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.ROLLBACK_COMPLETED -> {
                storage.cleanupSession(dto.sessionId)
                journal.delete()
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.COMPLETED -> {
                storage.cleanupSession(dto.sessionId)
                journal.delete()
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.RECOVERY_REQUIRED -> {
                RestoreRecoveryResult.RecoveryRequired(dto.sessionId)
            }
        }
    }

    private suspend fun performFullRollback(dto: RestoreJournalDto) {
        // 1. Read and validate rollback snapshot
        val snapshotFile = storage.getRollbackSnapshotFile(dto.sessionId)
        if (!snapshotFile.exists()) {
            throw IllegalStateException("Rollback snapshot missing")
        }

        val rollback = try {
            codecs.reader.decodeFromString<RestoreDatabaseRollbackSnapshot>(snapshotFile.readText())
        } catch (e: Exception) {
            throw IllegalStateException("Rollback snapshot corrupt", e)
        }

        // 2. Require non-null previousPreferences
        val prevPrefs = dto.previousPreferences ?: throw IllegalStateException("Previous preferences missing in journal")

        // 3. Write or preserve ROLLING_BACK
        if (dto.phase != RestorePhase.ROLLING_BACK) {
            journal.write(dto.copy(phase = RestorePhase.ROLLING_BACK))
        }

        // 4. Restore database
        databaseApplier.restoreRollback(rollback)
        
        // 5. Restore preferences
        preferencesApplier.apply(prevPrefs)

        // 6. Verify database
        if (!databaseApplier.verifyMatchesRollback(rollback)) {
            throw IllegalStateException("Rollback database verification failed")
        }

        // 7. Verify preferences
        val currentPrefs = preferencesApplier.captureRollback()
        if (currentPrefs != prevPrefs) {
            throw IllegalStateException("Rollback preferences verification failed")
        }

        // 8. Write ROLLBACK_COMPLETED
        journal.write(dto.copy(phase = RestorePhase.ROLLBACK_COMPLETED))

        // 9. Cleanup
        storage.cleanupSession(dto.sessionId)

        // 10. Delete journal
        journal.delete()
    }
    
    suspend fun retryRecovery(): RestoreRecoveryResult {
        return recoverIfNeeded()
    }
}
