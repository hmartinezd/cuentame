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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    companion object {
        private const val KEY_LAST_OP_ID = "last_op_id"
        private const val KEY_ACTIVE_OP_ID = "active_op_id"
        private const val KEY_PHASE = "phase"
    }

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<BackupUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var destinationPickerPreparationJob: Job? = null
    private var activeBackupJob: Job? = null

    private val operationTokenGenerator = AtomicLong(savedStateHandle.get<Long>(KEY_LAST_OP_ID) ?: 0L)
    @Volatile private var activeOperationToken: Long = savedStateHandle.get<Long>(KEY_ACTIVE_OP_ID) ?: -1L

    private val mutex = Mutex()

    init {
        restoreState()
    }

    private fun restoreState() {
        val activeId = savedStateHandle.get<Long>(KEY_ACTIVE_OP_ID) ?: return
        val phase = savedStateHandle.get<String>(KEY_PHASE) ?: return
        val opId = BackupOperationId(activeId)

        _uiState.value = when (phase) {
            "IDLE" -> BackupUiState.Idle
            "WAITING" -> BackupUiState.WaitingForDestination(opId)
            "CREATING", "VALIDATING" -> BackupUiState.Error(opId, BackupResult.Error.SystemIOFailure(Exception("Backup interrupted by process death")))
            "CANCELLED" -> BackupUiState.Cancelled(opId)
            else -> BackupUiState.Idle
        }
    }

    fun onCreateBackupRequested() {
        viewModelScope.launch {
            mutex.withLock {
                val current = _uiState.value
                if (!isTerminalState(current)) return@withLock

                val token = operationTokenGenerator.incrementAndGet()
                activeOperationToken = token
                persistState(token, token, "WAITING")

                val opId = BackupOperationId(token)
                _uiState.value = BackupUiState.WaitingForDestination(opId)
                launchPickerPrep(opId, token)
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
                    val errorState = BackupUiState.Error(opId, BackupResult.Error.FilenamePreparationFailure(e))
                    _uiState.value = errorState
                    persistState(activeOperationToken, activeOperationToken, "ERROR")
                }
            }
        }
    }

    fun onFileSelected(operationId: BackupOperationId, uri: String) {
        if (operationId.value != activeOperationToken) return

        val current = _uiState.value
        if (current !is BackupUiState.WaitingForDestination || current.operationId != operationId) {
            return
        }

        _uiState.value = BackupUiState.Creating(operationId)
        persistState(activeOperationToken, activeOperationToken, "CREATING")

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
                    persistState(activeOperationToken, activeOperationToken, "ERROR")
                }
            }
        }
    }

    fun onPickerCancelled(operationId: BackupOperationId) {
        if (operationId.value != activeOperationToken) return
        val current = _uiState.value
        if (current is BackupUiState.WaitingForDestination && current.operationId == operationId) {
            _uiState.value = BackupUiState.Cancelled(operationId)
            persistState(activeOperationToken, activeOperationToken, "CANCELLED")
            destinationPickerPreparationJob?.cancel()
        }
    }

    private fun applyOperationStatus(operationId: BackupOperationId, status: BackupOperationStatus) {
        when (status) {
            is BackupOperationStatus.Creating -> {
                _uiState.value = BackupUiState.Creating(operationId)
                persistState(activeOperationToken, activeOperationToken, "CREATING")
            }
            is BackupOperationStatus.Validating -> {
                _uiState.value = BackupUiState.Validating(operationId)
                persistState(activeOperationToken, activeOperationToken, "VALIDATING")
            }
            is BackupOperationStatus.Success -> {
                _uiState.value = BackupUiState.Success(operationId, status.manifest)
                persistState(activeOperationToken, activeOperationToken, "SUCCESS")
            }
            is BackupOperationStatus.Error -> {
                if (status.result is BackupResult.Error.OperationCancelled) {
                    _uiState.value = BackupUiState.Cancelled(operationId)
                    persistState(activeOperationToken, activeOperationToken, "CANCELLED")
                } else {
                    _uiState.value = BackupUiState.Error(operationId, status.result)
                    persistState(activeOperationToken, activeOperationToken, "ERROR")
                }
            }
        }
    }

    private fun persistState(lastOpId: Long, activeOpId: Long, phase: String) {
        savedStateHandle[KEY_LAST_OP_ID] = lastOpId
        savedStateHandle[KEY_ACTIVE_OP_ID] = activeOpId
        savedStateHandle[KEY_PHASE] = phase
    }

    fun resetStatus() {
        val newToken = operationTokenGenerator.incrementAndGet()
        activeOperationToken = newToken
        persistState(newToken, newToken, "IDLE")
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
