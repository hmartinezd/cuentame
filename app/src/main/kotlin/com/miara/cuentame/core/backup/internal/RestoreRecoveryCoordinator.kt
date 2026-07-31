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
                // Step 10.1: Corrupt journal results in RecoveryRequired
                RestoreRecoveryResult.RecoveryRequired("unknown")
            }
            is RestoreJournalReadResult.Present -> {
                val dto = result.journal
                try {
                    handlePhase(dto)
                } catch (e: Exception) {
                    journal.write(dto.copy(phase = RestorePhase.RECOVERY_REQUIRED))
                    RestoreRecoveryResult.RecoveryRequired(dto.sessionId)
                }
            }
        }
    }

    private suspend fun handlePhase(dto: RestoreJournalDto): RestoreRecoveryResult {
        return when (dto.phase) {
            RestorePhase.ROLLBACK_CAPTURED -> {
                // Mutation not started, just cleanup
                storage.cleanupSession(dto.sessionId)
                journal.delete()
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.MUTATION_STARTED,
            RestorePhase.DATABASE_APPLIED,
            RestorePhase.PREFERENCES_APPLIED,
            RestorePhase.ROLLING_BACK -> {
                performRollback(dto)
                journal.delete()
                RestoreRecoveryResult.Recovered(dto.sessionId)
            }
            RestorePhase.ROLLBACK_COMPLETED,
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

    private suspend fun performRollback(dto: RestoreJournalDto) {
        // 1. Load Rollback Snapshot
        val snapshotFile = storage.getRollbackSnapshotFile(dto.sessionId)
        if (!snapshotFile.exists()) {
            // Step 12.4: Missing rollback snapshot after mutation began
            throw IllegalStateException("Rollback snapshot missing for active restore session")
        }

        val rollback = try {
            codecs.reader.decodeFromString<RestoreDatabaseRollbackSnapshot>(snapshotFile.readText())
        } catch (e: Exception) {
            throw IllegalStateException("Rollback snapshot corrupt", e)
        }

        // 2. Restore Preferences
        dto.previousPreferences?.let { preferencesApplier.apply(it) }
        
        // 3. Restore Database
        databaseApplier.restoreRollback(rollback)
        
        // 4. Verify Rollback
        if (!databaseApplier.verifyMatchesRollback(rollback)) {
            throw IllegalStateException("Rollback verification failed")
        }
        
        storage.cleanupSession(dto.sessionId)
    }
    
    suspend fun retryRecovery(): RestoreRecoveryResult {
        return recoverIfNeeded()
    }
}
