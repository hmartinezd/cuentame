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
    @Volatile private var activeToken = 0L

    fun onCreateBackupRequested() {
        var transitioned = false
        while (true) {
            val current = _uiState.value
            if (current != BackupUiState.Idle &&
                current !is BackupUiState.Success &&
                current !is BackupUiState.Error &&
                current != BackupUiState.Cancelled
            ) {
                return
            }
            if (_uiState.compareAndSet(current, BackupUiState.WaitingForDestination)) {
                transitioned = true
                break
            }
        }
        if (!transitioned) return

        val token = operationTokenGenerator.incrementAndGet()
        activeToken = token

        destinationPickerPreparationJob?.cancel()
        destinationPickerPreparationJob = viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant()
                val suggestedName = BackupFilenameGenerator.generate(restaurant?.name, timeProvider.now())
                if (activeToken == token && _uiState.value == BackupUiState.WaitingForDestination) {
                    _events.emit(BackupUiEvent.LaunchFilePicker(suggestedName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeToken == token) {
                    _uiState.value = BackupUiState.Error(BackupResult.Error.FilenamePreparationFailure(e))
                }
            }
        }
    }

    fun onFileSelected(uri: String) {
        // Atomic transition from WaitingForDestination to Creating.
        // Duplicate callbacks fail compareAndSet and return immediately without repository calls or job cancellations.
        if (!_uiState.compareAndSet(BackupUiState.WaitingForDestination, BackupUiState.Creating)) {
            return
        }

        val token = activeToken
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
        activeBackupJob = viewModelScope.launch {
            try {
                backupRepository.createBackup(uri).collect { status ->
                    if (activeToken != token) return@collect
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
        if (_uiState.compareAndSet(BackupUiState.WaitingForDestination, BackupUiState.Cancelled)) {
            destinationPickerPreparationJob?.cancel()
        }
    }

    fun resetStatus() {
        activeToken = operationTokenGenerator.incrementAndGet()
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
        _uiState.value = BackupUiState.Idle
    }

    fun resetState() = resetStatus()

    override fun onCleared() {
        super.onCleared()
        activeToken = operationTokenGenerator.incrementAndGet()
        destinationPickerPreparationJob?.cancel()
        activeBackupJob?.cancel()
    }
}
