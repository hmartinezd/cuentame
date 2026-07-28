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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var destinationPickerPreparationJob: Job? = null
    private var activeBackupJob: Job? = null

    fun onCreateBackupRequested() {
        if (!canStartOperation()) return
        
        _uiState.value = BackupUiState.WaitingForDestination
        destinationPickerPreparationJob?.cancel()
        destinationPickerPreparationJob = viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant()
                val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())
                _events.emit(BackupUiEvent.LaunchFilePicker(suggestedName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(BackupResult.Error.Unknown(e))
            }
        }
    }

    fun onFileSelected(uri: String) {
        if (_uiState.value != BackupUiState.WaitingForDestination) return
        
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
        activeBackupJob = viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun onPickerCancelled() {
        if (_uiState.value == BackupUiState.WaitingForDestination) {
            destinationPickerPreparationJob?.cancel()
            _uiState.value = BackupUiState.Cancelled
        }
    }

    fun resetStatus() {
        val current = _uiState.value
        if (current is BackupUiState.Success || current is BackupUiState.Error || current == BackupUiState.Cancelled) {
            _uiState.value = BackupUiState.Idle
        }
    }

    private fun canStartOperation(): Boolean {
        val current = _uiState.value
        return current == BackupUiState.Idle || 
               current is BackupUiState.Success || 
               current is BackupUiState.Error || 
               current == BackupUiState.Cancelled
    }

    override fun onCleared() {
        super.onCleared()
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
    }
}
