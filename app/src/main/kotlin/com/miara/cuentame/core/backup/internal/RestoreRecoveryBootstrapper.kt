package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.RestoreRecoveryResult
import com.miara.cuentame.core.backup.api.RestoreStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryBootstrapper @Inject constructor(
    private val recoveryCoordinator: RestoreRecoveryCoordinator,
    private val operationGate: RestoreOperationGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun bootstrap() {
        scope.launch {
            operationGate.mutex.withLock {
                operationGate.updateRecoveryState(RestoreStartupState.Recovering)
                try {
                    val result = recoveryCoordinator.recoverIfNeeded()
                    val terminalState = when (result) {
                        RestoreRecoveryResult.NoRecoveryNeeded -> RestoreStartupState.Ready
                        is RestoreRecoveryResult.Recovered -> RestoreStartupState.Recovered(result.sessionId)
                        is RestoreRecoveryResult.RecoveryRequired -> RestoreStartupState.RecoveryRequired
                    }
                    operationGate.updateRecoveryState(terminalState)
                } catch (e: Exception) {
                    operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
                }
            }
        }
    }
}
