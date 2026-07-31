package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.internal.RestoreRecoveryResult
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.serialization.Serializable

@Serializable
enum class RestorePhase {
    IDLE,
    STAGING,
    STAGED,
    ROLLBACK_CAPTURED,
    DATABASE_APPLIED,
    ATTACHMENTS_APPLIED,
    PREFERENCES_APPLIED,
    COMPLETED,
    ROLLING_BACK,
    ROLLBACK_COMPLETED,
    RECOVERY_REQUIRED
}

interface BackupRestoreCoordinator {
    suspend fun inspect(
        source: BackupDocumentUri
    ): BackupArchiveInspectionResult

    suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint
    ): BackupRestoreApplyResult

    suspend fun recoverIfNeeded(): RestoreRecoveryResult
}

sealed interface BackupRestoreApplyResult {
    data object Success : BackupRestoreApplyResult
    data class Failure(val reason: BackupRestoreFailure) : BackupRestoreApplyResult
}
