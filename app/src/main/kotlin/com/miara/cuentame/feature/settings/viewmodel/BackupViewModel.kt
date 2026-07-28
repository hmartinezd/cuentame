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
import java.util.concurrent.atomic.AtomicLong
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
    
    private val operationTokenGenerator = AtomicLong(0L)
    @Volatile private var activeOperationToken: Long = -1

    /**
     * Attempts to transition to WaitingForDestination and launch picker preparation.
     * Atomically ignores request if an operation is already in progress.
     */
    fun onCreateBackupRequested() {
        // Atomic transition from any terminal state to WaitingForDestination
        while (true) {
            val current = _uiState.value
            if (!isTerminalState(current)) return // Already in progress

            if (_uiState.compareAndSet(current, BackupUiState.WaitingForDestination)) {
                break // Success
            }
            // If CAS failed, someone else changed state; loop to check if it's still a terminal state
        }

        val token = operationTokenGenerator.incrementAndGet()
        activeOperationToken = token

        cancelAllJobs()
        destinationPickerPreparationJob = viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant()
                val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())
                
                // Only emit if this is still the current operation and we are still waiting
                if (activeOperationToken == token && _uiState.value == BackupUiState.WaitingForDestination) {
                    _events.emit(BackupUiEvent.LaunchFilePicker(suggestedName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupUiState.Error(BackupResult.Error.FilenamePreparationFailure(e))
                }
            }
        }
    }

    /**
     * Handles the file selection result from the system picker.
     * Atomically transitions to Creating.
     */
    fun onFileSelected(uri: String) {
        val token = activeOperationToken
        
        // Atomic transition: only proceed if we were exactly WaitingForDestination
        if (!_uiState.compareAndSet(BackupUiState.WaitingForDestination, BackupUiState.Creating)) {
            return // Ignore duplicate or stale callback
        }

        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
        
        activeBackupJob = viewModelScope.launch {
            try {
                backupRepository.createBackup(uri).collect { status ->
                    // Only apply status if this job still owns the operation
                    if (activeOperationToken != token) return@collect
                    
                    applyOperationStatus(status)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun applyOperationStatus(status: BackupOperationStatus) {
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

    fun onPickerCancelled() {
        if (_uiState.compareAndSet(BackupUiState.WaitingForDestination, BackupUiState.Cancelled)) {
            destinationPickerPreparationJob?.cancel()
        }
    }

    fun resetStatus() {
        activeOperationToken = operationTokenGenerator.incrementAndGet()
        cancelAllJobs()
        _uiState.value = BackupUiState.Idle
    }

    fun resetState() = resetStatus()

    private fun isTerminalState(state: BackupUiState): Boolean =
        state == BackupUiState.Idle || 
        state is BackupUiState.Success || 
        state is BackupUiState.Error || 
        state == BackupUiState.Cancelled

    private fun cancelAllJobs() {
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()
    }
}
