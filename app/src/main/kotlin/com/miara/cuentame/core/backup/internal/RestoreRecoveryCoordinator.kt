package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.backup.api.RestorePhase
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryCoordinator @Inject constructor(
    private val journal: RestoreJournal,
    private val storage: InternalBackupRestoreStorage,
    private val databaseApplier: RestoreDatabaseApplier,
    private val attachmentInstaller: RestoreAttachmentInstaller,
    private val preferencesApplier: RestorePreferencesApplier,
    private val codecs: BackupJsonCodecs
) {
    suspend fun recoverIfNeeded(): RestoreRecoveryResult {
        val dto = journal.read() ?: return RestoreRecoveryResult.NoRecoveryNeeded

        return try {
            when (dto.phase) {
                RestorePhase.IDLE,
                RestorePhase.STAGING,
                RestorePhase.STAGED -> {
                    // No mutation occurred, just cleanup
                    storage.cleanupSession(dto.sessionId)
                    journal.delete()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                }
                RestorePhase.ROLLBACK_CAPTURED -> {
                    // Mutation might have started but we have everything to rollback if needed.
                    // Actually, at this phase we haven't started mutation yet.
                    storage.cleanupSession(dto.sessionId)
                    journal.delete()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                }
                RestorePhase.DATABASE_APPLIED,
                RestorePhase.ATTACHMENTS_APPLIED,
                RestorePhase.PREFERENCES_APPLIED,
                RestorePhase.ROLLING_BACK -> {
                    // Mutation occurred, must rollback
                    performRollback(dto)
                    journal.delete()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                }
                RestorePhase.COMPLETED -> {
                    // Successful restore, just need to cleanup rollback artifacts
                    storage.cleanupSession(dto.sessionId)
                    journal.delete()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                }
                RestorePhase.ROLLBACK_COMPLETED -> {
                    storage.cleanupSession(dto.sessionId)
                    journal.delete()
                    RestoreRecoveryResult.Recovered(dto.sessionId)
                }
                RestorePhase.RECOVERY_REQUIRED -> {
                    RestoreRecoveryResult.RecoveryRequired(dto.sessionId)
                }
            }
        } catch (e: Exception) {
            journal.write(dto.copy(phase = RestorePhase.RECOVERY_REQUIRED))
            RestoreRecoveryResult.RecoveryRequired(dto.sessionId)
        }
    }

    private suspend fun performRollback(dto: RestoreJournalDto) {
        // 1. Restore Preferences
        dto.previousPreferences?.let { preferencesApplier.apply(it) }
        
        // 2. Restore Attachments
        attachmentInstaller.rollback(dto.sessionId)
        
        // 3. Restore Database
        val snapshotFile = storage.getRollbackSnapshotFile(dto.sessionId)
        if (snapshotFile.exists()) {
            val snapshot = codecs.reader.decodeFromString<BackupSnapshotDto>(snapshotFile.readText())
            databaseApplier.replaceWith(snapshot)
        }
        
        storage.cleanupSession(dto.sessionId)
    }
}

sealed interface RestoreRecoveryResult {
    data object NoRecoveryNeeded : RestoreRecoveryResult
    data class Recovered(val sessionId: String) : RestoreRecoveryResult
    data class RecoveryRequired(val sessionId: String) : RestoreRecoveryResult
}
