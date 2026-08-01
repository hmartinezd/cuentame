package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.RestoreRecoveryResult
import com.miara.cuentame.core.backup.api.RestoreStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryBootstrapper @Inject constructor(
    private val recoveryCoordinator: RestoreRecoveryCoordinator,
    private val operationGate: RestoreOperationGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun bootstrap() {
        if (!started.compareAndSet(false, true)) return
        
        // Synchronously publish Recovering before returning
        operationGate.updateRecoveryState(RestoreStartupState.Recovering)

        scope.launch {
            operationGate.mutex.withLock {
                try {
                    val terminalState = when (val result = recoveryCoordinator.recoverIfNeeded()) {
                        RestoreRecoveryResult.NoRecoveryNeeded -> RestoreStartupState.Ready
                        is RestoreRecoveryResult.Recovered -> RestoreStartupState.Recovered(result.sessionId)
                        is RestoreRecoveryResult.RecoveryRequired -> RestoreStartupState.RecoveryRequired
                    }
                    operationGate.updateRecoveryState(terminalState)
                } catch (_: Exception) {
                    operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
                }
            }
        }
    }
}
