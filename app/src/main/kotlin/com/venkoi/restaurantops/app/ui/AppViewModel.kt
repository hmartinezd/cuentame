package com.venkoi.restaurantops.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.backup.api.RestoreStartupState
import com.venkoi.restaurantops.core.backup.internal.RestoreOperationGate
import com.venkoi.restaurantops.core.domain.usecase.AppStartState
import com.venkoi.restaurantops.core.domain.usecase.ResolveAppStartStateUseCase
import com.venkoi.restaurantops.core.preferences.model.AppPreferences
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
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
    private val restoreCoordinator: com.venkoi.restaurantops.core.backup.api.BackupRestoreCoordinator
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
