package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.BackupFilenameGenerator
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val operationGuard = Mutex()

    fun onCreateBackupRequested() {
        viewModelScope.launch {
            operationGuard.withLock {
                val current = _uiState.value
                if (current is BackupUiState.WaitingForDestination || 
                    current is BackupUiState.Creating || 
                    current is BackupUiState.Validating) return@withLock
                
                _uiState.value = BackupUiState.WaitingForDestination
                val restaurant = restaurantRepository.getRestaurant()
                val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())
                _events.emit(BackupUiEvent.LaunchFilePicker(suggestedName))
            }
        }
    }

    fun onFileSelected(uri: String) {
        viewModelScope.launch {
            operationGuard.withLock {
                val current = _uiState.value
                if (current is BackupUiState.Creating || current is BackupUiState.Validating) return@withLock

                backupRepository.createBackup(uri).collect { status ->
                    when (status) {
                        is BackupOperationStatus.Creating -> _uiState.value = BackupUiState.Creating
                        is BackupOperationStatus.Validating -> _uiState.value = BackupUiState.Validating
                        is BackupOperationStatus.Success -> _uiState.value = BackupUiState.Success(status.manifest)
                        is BackupOperationStatus.Error -> {
                            _uiState.value = if (status.result is BackupResult.Error.OperationCancelled) {
                                BackupUiState.Cancelled
                            } else {
                                BackupUiState.Error(status.result)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onPickerCancelled() {
        viewModelScope.launch {
            operationGuard.withLock {
                if (_uiState.value is BackupUiState.WaitingForDestination) {
                    _uiState.value = BackupUiState.Cancelled
                }
            }
        }
    }

    fun resetStatus() {
        _uiState.value = BackupUiState.Idle
    }
}
