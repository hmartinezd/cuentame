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
class DefaultRecoveryBootstrapper @Inject constructor(
    private val recoveryCoordinator: RestoreRecoveryCoordinator,
    private val operationGate: RestoreOperationGate,
    private val cleanupCoordinator: PurchaseAttachmentCleanupCoordinator
) : RecoveryBootstrapper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    override fun bootstrap() {
        if (!started.compareAndSet(false, true)) return
        
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

                    if (terminalState !is RestoreStartupState.RecoveryRequired) {
                        cleanupCoordinator.cleanupOrphans()
                    }
                } catch (_: Exception) {
                    operationGate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
                }
            }
        }
    }
}
