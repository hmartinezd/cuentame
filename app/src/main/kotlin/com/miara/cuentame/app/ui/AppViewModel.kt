package com.miara.cuentame.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.domain.usecase.AppStartState
import com.miara.cuentame.core.domain.usecase.ResolveAppStartStateUseCase
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppPreferencesState {
    data object Loading : AppPreferencesState
    data class Ready(val preferences: AppPreferences) : AppPreferencesState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    resolveAppStartStateUseCase: ResolveAppStartStateUseCase,
    preferencesRepository: AppPreferencesRepository,
    operationGate: RestoreOperationGate,
    private val restoreCoordinator: com.miara.cuentame.core.backup.api.BackupRestoreCoordinator
) : ViewModel() {

    val startState: StateFlow<AppStartState> = resolveAppStartStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppStartState.Loading
        )

    val preferencesState: StateFlow<AppPreferencesState> = preferencesRepository.observePreferences()
        .map { AppPreferencesState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferencesState.Loading
        )

    val recoveryState: StateFlow<RestoreStartupState> = operationGate.recoveryState

    fun retryRecovery() {
        viewModelScope.launch {
            restoreCoordinator.retryRecovery()
        }
    }
}
