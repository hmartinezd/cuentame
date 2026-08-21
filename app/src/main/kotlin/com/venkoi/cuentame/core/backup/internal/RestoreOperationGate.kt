package com.venkoi.cuentame.core.backup.internal

import com.venkoi.cuentame.core.backup.api.RestoreStartupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreOperationGate @Inject constructor() {
    val mutex = Mutex()

    private val _recoveryState = MutableStateFlow<RestoreStartupState>(RestoreStartupState.NotStarted)
    val recoveryState: StateFlow<RestoreStartupState> = _recoveryState.asStateFlow()

    fun updateRecoveryState(state: RestoreStartupState) {
        _recoveryState.value = state
    }

    suspend fun awaitTerminalState(): RestoreStartupState =
        recoveryState.first { it.isTerminal }

    suspend fun <T> withOperationalLock(
        onRecoveryRequired: suspend () -> T,
        block: suspend () -> T
    ): T {
        while (true) {
            awaitTerminalState()

            mutex.withLock {
                when (val state = recoveryState.value) {
                    RestoreStartupState.Ready,
                    is RestoreStartupState.Recovered -> {
                        return block()
                    }

                    RestoreStartupState.RecoveryRequired -> {
                        return onRecoveryRequired()
                    }

                    RestoreStartupState.NotStarted,
                    RestoreStartupState.Recovering -> {
                        // State changed or we raced. Release lock and wait again.
                    }
                }
            }
        }
    }
}
