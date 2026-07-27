package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.BackupFilenameGenerator
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object WaitingForDestination : BackupUiState
    data object Creating : BackupUiState
    data object Validating : BackupUiState
    data class Success(val manifest: BackupManifest) : BackupUiState
    data class Error(val result: BackupResult.Error) : BackupUiState
    data object Cancelled : BackupUiState
}

sealed interface BackupUiEvent {
    data class LaunchFilePicker(val suggestedName: String) : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restaurantRepository: com.miara.cuentame.core.domain.repository.RestaurantRepository,
    private val timeProvider: com.miara.cuentame.core.common.time.TimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun onCreateBackupRequested() {
        if (_uiState.value != BackupUiState.Idle && _uiState.value !is BackupUiState.Success && _uiState.value !is BackupUiState.Error && _uiState.value != BackupUiState.Cancelled) return
        
        viewModelScope.launch {
            _uiState.value = BackupUiState.WaitingForDestination
            val restaurant = restaurantRepository.getRestaurant()
            val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())
            _events.emit(BackupUiEvent.LaunchFilePicker(suggestedName))
        }
    }

    fun onFileSelected(uri: String) {
        viewModelScope.launch {
            backupRepository.createBackup(uri).collect { status ->
                when (status) {
                    is BackupOperationStatus.Creating -> _uiState.value = BackupUiState.Creating
                    is BackupOperationStatus.Validating -> _uiState.value = BackupUiState.Validating
                    is BackupOperationStatus.Success -> _uiState.value = BackupUiState.Success(status.manifest)
                    is BackupOperationStatus.Error -> {
                        if (status.result is BackupResult.Error.OperationCancelled) {
                            _uiState.value = BackupUiState.Cancelled
                        } else {
                            _uiState.value = BackupUiState.Error(status.result)
                        }
                    }
                }
            }
        }
    }

    fun onPickerCancelled() {
        _uiState.value = BackupUiState.Cancelled
    }

    fun resetStatus() {
        _uiState.value = BackupUiState.Idle
    }
}
