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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

enum class SavedPickerLaunchState {
    NONE,
    PENDING,
    CONSUMED
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
        private const val KEY_PICKER_STATE = "picker_state"
        private const val KEY_SUGGESTED_NAME = "suggested_name"
    }

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<BackupUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var destinationPickerPreparationJob: Job? = null
    private var activeBackupJob: Job? = null

    private val operationStateLock = Any()
    
    private val operationTokenGenerator: AtomicLong
    private var activeOperationToken: Long

    init {
        val lastId = savedStateHandle.get<Long>(KEY_LAST_OP_ID) ?: 0L
        val activeId = savedStateHandle.get<Long>(KEY_ACTIVE_OP_ID) ?: -1L
        operationTokenGenerator = AtomicLong(maxOf(lastId, activeId))
        activeOperationToken = activeId
        
        restoreState()
    }

    private fun restoreState() {
        var eventToSend: BackupUiEvent? = null

        synchronized(operationStateLock) {
            val lastId = savedStateHandle.get<Long>(KEY_LAST_OP_ID) ?: -1L
            val activeId = activeOperationToken
            val phase = savedStateHandle.get<String>(KEY_PHASE) ?: return

            when (phase) {
                "WAITING" -> {
                    if (activeId <= 0L) {
                        _uiState.value = BackupUiState.Idle
                        return
                    }
                    val opId = BackupOperationId(activeId)
                    val pickerState = savedStateHandle.get<String>(KEY_PICKER_STATE)
                    val suggestedName = savedStateHandle.get<String>(KEY_SUGGESTED_NAME)
                    
                    if (pickerState == SavedPickerLaunchState.PENDING.name && !suggestedName.isNullOrBlank()) {
                        eventToSend = BackupUiEvent.LaunchFilePicker(opId, suggestedName)
                    }
                    _uiState.value = BackupUiState.WaitingForDestination(opId)
                }
                "CREATING", "VALIDATING" -> {
                    if (activeId <= 0L) {
                        _uiState.value = BackupUiState.Error(
                            if (lastId > 0L) BackupOperationId(lastId) else null,
                            BackupResult.Error.OperationInterrupted
                        )
                        return
                    }
                    val opId = BackupOperationId(activeId)
                    invalidateActiveTokenLocked()
                    _uiState.value = BackupUiState.Error(opId, BackupResult.Error.OperationInterrupted)
                    persistStateLocked(opId.value, -1L, "INTERRUPTED", SavedPickerLaunchState.NONE, null)
                }
                "INTERRUPTED" -> {
                    _uiState.value = BackupUiState.Error(
                        if (lastId > 0L) BackupOperationId(lastId) else null,
                        BackupResult.Error.OperationInterrupted
                    )
                }
                "CANCELLED" -> {
                    _uiState.value = if (lastId > 0L) BackupUiState.Cancelled(BackupOperationId(lastId)) else BackupUiState.Idle
                }
                "SUCCESS", "ERROR" -> {
                    _uiState.value = BackupUiState.Idle
                }
                else -> _uiState.value = BackupUiState.Idle
            }
        }

        eventToSend?.let { event ->
            viewModelScope.launch {
                _events.send(event)
            }
        }
    }

    fun onCreateBackupRequested() {
        var jobToStart: Job? = null
        
        synchronized(operationStateLock) {
            if (!isTerminalState(_uiState.value)) return

            val token = operationTokenGenerator.incrementAndGet()
            activeOperationToken = token
            persistStateLocked(token, token, "WAITING", SavedPickerLaunchState.NONE, null)

            val opId = BackupOperationId(token)
            
            val newJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
                runPickerPreparation(opId, token)
            }

            cancelAllJobsLocked()
            destinationPickerPreparationJob = newJob
            jobToStart = newJob

            _uiState.value = BackupUiState.WaitingForDestination(opId)
        }
        
        jobToStart?.start()
    }

    private suspend fun runPickerPreparation(opId: BackupOperationId, token: Long) {
        try {
            val restaurant = restaurantRepository.getRestaurant()
            val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())

            var eventToSend: BackupUiEvent? = null

            synchronized(operationStateLock) {
                if (activeOperationToken == token && _uiState.value is BackupUiState.WaitingForDestination) {
                    savedStateHandle[KEY_SUGGESTED_NAME] = suggestedName
                    savedStateHandle[KEY_PICKER_STATE] = SavedPickerLaunchState.PENDING.name
                    eventToSend = BackupUiEvent.LaunchFilePicker(opId, suggestedName)
                }
            }
            
            eventToSend?.let { _events.send(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(operationStateLock) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupUiState.Error(opId, BackupResult.Error.FilenamePreparationFailure)
                    invalidateActiveTokenLocked()
                    persistStateLocked(opId.value, -1L, "ERROR", SavedPickerLaunchState.NONE, null)
                }
            }
        }
    }

    fun consumePickerLaunch(operationId: BackupOperationId): Boolean = synchronized(operationStateLock) {
        if (operationId.value != activeOperationToken) return false
        val current = _uiState.value
        if (current !is BackupUiState.WaitingForDestination || current.operationId != operationId) return false
        
        val savedState = savedStateHandle.get<String>(KEY_PICKER_STATE)
        if (savedState != SavedPickerLaunchState.PENDING.name) return false
        
        savedStateHandle[KEY_PICKER_STATE] = SavedPickerLaunchState.CONSUMED.name
        return true
    }

    fun onFileSelected(operationId: BackupOperationId, uri: String) {
        val token = operationId.value
        var jobToStart: Job? = null

        synchronized(operationStateLock) {
            if (operationId.value != activeOperationToken) return

            val current = _uiState.value
            if (current !is BackupUiState.WaitingForDestination || current.operationId != operationId) {
                return
            }

            val newJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
                collectBackupOperation(operationId, token, uri)
            }

            activeBackupJob?.cancel()
            activeBackupJob = newJob
            jobToStart = newJob

            _uiState.value = BackupUiState.Creating(operationId)
            persistStateLocked(activeOperationToken, activeOperationToken, "CREATING", SavedPickerLaunchState.CONSUMED, null)
        }

        jobToStart?.start()
    }

    private suspend fun collectBackupOperation(operationId: BackupOperationId, token: Long, uri: String) {
        try {
            backupRepository.createBackup(uri).collect { status ->
                synchronized(operationStateLock) {
                    if (activeOperationToken != token) return@synchronized
                    applyOperationStatusLocked(operationId, status)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(operationStateLock) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupUiState.Error(operationId, BackupResult.Error.SystemIOFailure)
                    invalidateActiveTokenLocked()
                    persistStateLocked(operationId.value, -1L, "ERROR", SavedPickerLaunchState.CONSUMED, null)
                }
            }
        }
    }

    fun onPickerCancelled(operationId: BackupOperationId) = synchronized(operationStateLock) {
        if (operationId.value != activeOperationToken) return
        val current = _uiState.value
        if (current is BackupUiState.WaitingForDestination && current.operationId == operationId) {
            _uiState.value = BackupUiState.Cancelled(operationId)
            val lastId = activeOperationToken
            invalidateActiveTokenLocked()
            persistStateLocked(lastId, -1L, "CANCELLED", SavedPickerLaunchState.CONSUMED, null)
            cancelAllJobsLocked()
        }
    }

    private fun applyOperationStatusLocked(operationId: BackupOperationId, status: BackupOperationStatus) {
        when (status) {
            is BackupOperationStatus.Creating -> {
                _uiState.value = BackupUiState.Creating(operationId)
                persistStateLocked(activeOperationToken, activeOperationToken, "CREATING", SavedPickerLaunchState.CONSUMED, null)
            }
            is BackupOperationStatus.Validating -> {
                _uiState.value = BackupUiState.Validating(operationId)
                persistStateLocked(activeOperationToken, activeOperationToken, "VALIDATING", SavedPickerLaunchState.CONSUMED, null)
            }
            is BackupOperationStatus.Success -> {
                _uiState.value = BackupUiState.Success(operationId, status.manifest)
                val lastId = activeOperationToken
                invalidateActiveTokenLocked()
                persistStateLocked(lastId, -1L, "SUCCESS", SavedPickerLaunchState.CONSUMED, null)
            }
            is BackupOperationStatus.Error -> {
                val lastId = activeOperationToken
                val terminalPhase = if (status.result is BackupResult.Error.OperationCancelled) "CANCELLED" else "ERROR"
                if (status.result is BackupResult.Error.OperationCancelled) {
                    _uiState.value = BackupUiState.Cancelled(operationId)
                } else {
                    _uiState.value = BackupUiState.Error(operationId, status.result)
                }
                invalidateActiveTokenLocked()
                persistStateLocked(lastId, -1L, terminalPhase, SavedPickerLaunchState.CONSUMED, null)
            }
        }
    }

    private fun invalidateActiveTokenLocked() {
        activeOperationToken = -1L
        savedStateHandle[KEY_ACTIVE_OP_ID] = -1L
        savedStateHandle[KEY_PICKER_STATE] = SavedPickerLaunchState.NONE.name
        savedStateHandle[KEY_SUGGESTED_NAME] = null
    }

    private fun persistStateLocked(lastOpId: Long, activeOpId: Long, phase: String, pickerState: SavedPickerLaunchState, suggestedName: String?) {
        savedStateHandle[KEY_LAST_OP_ID] = lastOpId
        savedStateHandle[KEY_ACTIVE_OP_ID] = activeOpId
        savedStateHandle[KEY_PHASE] = phase
        savedStateHandle[KEY_PICKER_STATE] = pickerState.name
        savedStateHandle[KEY_SUGGESTED_NAME] = suggestedName
    }

    fun resetStatus() = synchronized(operationStateLock) {
        val lastId = if (activeOperationToken != -1L) activeOperationToken else (savedStateHandle.get<Long>(KEY_LAST_OP_ID) ?: 0L)
        invalidateActiveTokenLocked()
        persistStateLocked(lastId, -1L, "IDLE", SavedPickerLaunchState.NONE, null)
        cancelAllJobsLocked()
        _uiState.value = BackupUiState.Idle
    }

    fun resetState() = resetStatus()

    private fun isTerminalState(state: BackupUiState): Boolean =
        state == BackupUiState.Idle ||
        state is BackupUiState.Success ||
        state is BackupUiState.Error ||
        state is BackupUiState.Cancelled

    private fun cancelAllJobsLocked() {
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        synchronized(operationStateLock) {
            cancelAllJobsLocked()
        }
        _events.close()
    }
}
