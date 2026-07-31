package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class RestorePhase {
    ROLLBACK_CAPTURED,
    MUTATION_STARTED,
    DATABASE_APPLIED,
    PREFERENCES_APPLIED,
    COMPLETED,
    ROLLING_BACK,
    ROLLBACK_COMPLETED,
    RECOVERY_REQUIRED
}

enum class BackupRestoreProgress {
    ValidatingBackup,
    PreparingRollback,
    RestoringData,
    RestoringSettings,
    Finalizing,
    RollingBack
}

sealed interface RestoreStartupState {
    data object NotStarted : RestoreStartupState
    data object Recovering : RestoreStartupState
    data object Ready : RestoreStartupState
    data class Recovered(val sessionId: String) : RestoreStartupState
    data object RecoveryRequired : RestoreStartupState

    val isTerminal: Boolean
        get() = this is Ready || this is Recovered || this is RecoveryRequired
}

interface BackupRestoreCoordinator {
    val startupState: StateFlow<RestoreStartupState>

    suspend fun inspect(
        source: BackupDocumentUri
    ): BackupArchiveInspectionResult

    suspend fun apply(
        source: BackupDocumentUri,
        expectedFingerprint: BackupArchiveFingerprint,
        onProgress: suspend (BackupRestoreProgress) -> Unit
    ): BackupRestoreApplyResult

    suspend fun retryRecovery(): RestoreRecoveryResult
}

sealed interface BackupRestoreApplyResult {
    data object Success : BackupRestoreApplyResult
    data class Failure(val reason: BackupRestoreFailure) : BackupRestoreApplyResult
}

sealed interface RestoreRecoveryResult {
    data object NoRecoveryNeeded : RestoreRecoveryResult
    data class Recovered(val sessionId: String) : RestoreRecoveryResult
    data class RecoveryRequired(val sessionId: String) : RestoreRecoveryResult
}
