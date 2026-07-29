package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@JvmInline
value class BackupOperationId(val value: Long)

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class WaitingForDestination(val operationId: BackupOperationId) : BackupUiState
    data class Creating(val operationId: BackupOperationId) : BackupUiState
    data class Validating(val operationId: BackupOperationId) : BackupUiState
    data class Success(val operationId: BackupOperationId, val manifest: BackupManifest) : BackupUiState
    data class Error(val operationId: BackupOperationId?, val error: BackupResult.Error) : BackupUiState
    data class Cancelled(val operationId: BackupOperationId) : BackupUiState
}

sealed interface BackupUiEvent {
    data class LaunchFilePicker(val operationId: BackupOperationId, val suggestedName: String) : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val _events = Channel<BackupUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var destinationPickerPreparationJob: Job? = null
    private var activeBackupJob: Job? = null

    private val operationTokenGenerator = AtomicLong(savedStateHandle.get<Long>("last_op_id") ?: 0L)
    @Volatile private var activeOperationToken: Long = savedStateHandle.get<Long>("active_op_id") ?: -1L

    fun onCreateBackupRequested() {
        while (true) {
            val current = _uiState.value
            if (!isTerminalState(current)) return // Already in progress

            val token = operationTokenGenerator.incrementAndGet()
            activeOperationToken = token
            savedStateHandle["last_op_id"] = token
            savedStateHandle["active_op_id"] = token

            val opId = BackupOperationId(token)
            if (_uiState.compareAndSet(current, BackupUiState.WaitingForDestination(opId))) {
                launchPickerPrep(opId, token)
                break
            }
        }
    }

    private fun launchPickerPrep(opId: BackupOperationId, token: Long) {
        cancelAllJobs()
        destinationPickerPreparationJob = viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant()
                val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())

                if (activeOperationToken == token && _uiState.value is BackupUiState.WaitingForDestination) {
                    _events.send(BackupUiEvent.LaunchFilePicker(opId, suggestedName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupUiState.Error(opId, BackupResult.Error.FilenamePreparationFailure(e))
                }
            }
        }
    }

    fun onFileSelected(operationId: BackupOperationId, uri: String) {
        if (operationId.value != activeOperationToken) return // Stale picker result

        val current = _uiState.value
        if (current !is BackupUiState.WaitingForDestination || current.operationId != operationId) {
            return // Duplicate or stale callback
        }

        if (!_uiState.compareAndSet(current, BackupUiState.Creating(operationId))) {
            return
        }

        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()

        val token = operationId.value
        activeBackupJob = viewModelScope.launch {
            try {
                backupRepository.createBackup(uri).collect { status ->
                    if (activeOperationToken != token) return@collect
                    applyOperationStatus(operationId, status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupUiState.Error(operationId, BackupResult.Error.SystemIOFailure(e))
                }
            }
        }
    }

    fun onPickerCancelled(operationId: BackupOperationId) {
        if (operationId.value != activeOperationToken) return
        val current = _uiState.value
        if (current is BackupUiState.WaitingForDestination && current.operationId == operationId) {
            if (_uiState.compareAndSet(current, BackupUiState.Cancelled(operationId))) {
                destinationPickerPreparationJob?.cancel()
            }
        }
    }

    private fun applyOperationStatus(operationId: BackupOperationId, status: BackupOperationStatus) {
        when (status) {
            is BackupOperationStatus.Creating -> _uiState.value = BackupUiState.Creating(operationId)
            is BackupOperationStatus.Validating -> _uiState.value = BackupUiState.Validating(operationId)
            is BackupOperationStatus.Success -> _uiState.value = BackupUiState.Success(operationId, status.manifest)
            is BackupOperationStatus.Error -> {
                _uiState.value = if (status.result is BackupResult.Error.OperationCancelled) {
                    BackupUiState.Cancelled(operationId)
                } else {
                    BackupUiState.Error(operationId, status.result)
                }
            }
        }
    }

    fun resetStatus() {
        val newToken = operationTokenGenerator.incrementAndGet()
        activeOperationToken = newToken
        savedStateHandle["last_op_id"] = newToken
        savedStateHandle["active_op_id"] = newToken
        cancelAllJobs()
        _uiState.value = BackupUiState.Idle
    }

    fun resetState() = resetStatus()

    private fun isTerminalState(state: BackupUiState): Boolean =
        state == BackupUiState.Idle ||
        state is BackupUiState.Success ||
        state is BackupUiState.Error ||
        state is BackupUiState.Cancelled

    private fun cancelAllJobs() {
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()
        _events.close()
    }
}
