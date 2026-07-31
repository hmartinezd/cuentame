package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.RestoreStartupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
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
}
