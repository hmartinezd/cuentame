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
        private const val KEY_PICKER_CONSUMED = "picker_consumed"
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
            "WAITING" -> BackupUiState.WaitingForDestination(opId)
            "CREATING", "VALIDATING" -> {
                // Invalidate active operation on process death
                activeOperationToken = -1L
                savedStateHandle[KEY_ACTIVE_OP_ID] = -1L
                BackupUiState.Error(opId, BackupResult.Error.OperationInterrupted)
            }
            "SUCCESS" -> BackupUiState.Idle // Don't persist success
            "CANCELLED" -> BackupUiState.Cancelled(opId)
            "ERROR" -> BackupUiState.Idle // Don't persist generic error
            else -> BackupUiState.Idle
        }
    }

    fun onCreateBackupRequested() {
        viewModelScope.launch {
            mutex.withLock {
                if (!isTerminalState(_uiState.value)) return@withLock

                val token = operationTokenGenerator.incrementAndGet()
                activeOperationToken = token
                persistState(token, token, "WAITING", consumed = false)

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
                mutex.withLock {
                    if (activeOperationToken == token) {
                        _uiState.value = BackupUiState.Error(opId, BackupResult.Error.FilenamePreparationFailure(e))
                        persistState(activeOperationToken, activeOperationToken, "ERROR", consumed = true)
                    }
                }
            }
        }
    }

    fun consumePickerLaunch(operationId: BackupOperationId): Boolean {
        if (operationId.value != activeOperationToken) return false
        if (_uiState.value !is BackupUiState.WaitingForDestination) return false
        
        val alreadyConsumed = savedStateHandle.get<Boolean>(KEY_PICKER_CONSUMED) ?: false
        if (alreadyConsumed) return false
        
        savedStateHandle[KEY_PICKER_CONSUMED] = true
        return true
    }

    fun onFileSelected(operationId: BackupOperationId, uri: String) {
        if (operationId.value != activeOperationToken) return

        val current = _uiState.value
        if (current !is BackupUiState.WaitingForDestination || current.operationId != operationId) {
            return
        }

        if (!_uiState.compareAndSet(current, BackupUiState.Creating(operationId))) {
            return
        }

        persistState(activeOperationToken, activeOperationToken, "CREATING", consumed = true)

        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()

        val token = operationId.value
        activeBackupJob = viewModelScope.launch {
            try {
                backupRepository.createBackup(uri).collect { status ->
                    mutex.withLock {
                        if (activeOperationToken != token) return@withLock
                        applyOperationStatus(operationId, status)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutex.withLock {
                    if (activeOperationToken == token) {
                        _uiState.value = BackupUiState.Error(operationId, BackupResult.Error.SystemIOFailure(e))
                        persistState(activeOperationToken, activeOperationToken, "ERROR", consumed = true)
                    }
                }
            }
        }
    }

    fun onPickerCancelled(operationId: BackupOperationId) {
        if (operationId.value != activeOperationToken) return
        val current = _uiState.value
        if (current is BackupUiState.WaitingForDestination && current.operationId == operationId) {
            if (_uiState.compareAndSet(current, BackupUiState.Cancelled(operationId))) {
                persistState(activeOperationToken, activeOperationToken, "CANCELLED", consumed = true)
                destinationPickerPreparationJob?.cancel()
            }
        }
    }

    private fun applyOperationStatus(operationId: BackupOperationId, status: BackupOperationStatus) {
        when (status) {
            is BackupOperationStatus.Creating -> {
                _uiState.value = BackupUiState.Creating(operationId)
                persistState(activeOperationToken, activeOperationToken, "CREATING", consumed = true)
            }
            is BackupOperationStatus.Validating -> {
                _uiState.value = BackupUiState.Validating(operationId)
                persistState(activeOperationToken, activeOperationToken, "VALIDATING", consumed = true)
            }
            is BackupOperationStatus.Success -> {
                _uiState.value = BackupUiState.Success(operationId, status.manifest)
                persistState(activeOperationToken, activeOperationToken, "SUCCESS", consumed = true)
            }
            is BackupOperationStatus.Error -> {
                if (status.result is BackupResult.Error.OperationCancelled) {
                    _uiState.value = BackupUiState.Cancelled(operationId)
                    persistState(activeOperationToken, activeOperationToken, "CANCELLED", consumed = true)
                } else {
                    _uiState.value = BackupUiState.Error(operationId, status.result)
                    persistState(activeOperationToken, activeOperationToken, "ERROR", consumed = true)
                }
            }
        }
    }

    private fun persistState(lastOpId: Long, activeOpId: Long, phase: String, consumed: Boolean) {
        savedStateHandle[KEY_LAST_OP_ID] = lastOpId
        savedStateHandle[KEY_ACTIVE_OP_ID] = activeOpId
        savedStateHandle[KEY_PHASE] = phase
        savedStateHandle[KEY_PICKER_CONSUMED] = consumed
    }

    fun resetStatus() {
        val newToken = operationTokenGenerator.incrementAndGet()
        activeOperationToken = newToken
        persistState(newToken, -1L, "IDLE", consumed = true)
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
