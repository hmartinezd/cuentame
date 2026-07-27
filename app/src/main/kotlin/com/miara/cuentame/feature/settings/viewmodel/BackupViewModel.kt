package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data object Creating : BackupUiState
    data object Validating : BackupUiState
    data class Success(val manifest: BackupManifest) : BackupUiState
    data class Error(val result: BackupResult.Error) : BackupUiState
}

sealed interface BackupUiEvent {
    data object LaunchFilePicker : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun onCreateBackupRequested() {
        if (_uiState.value is BackupUiState.Creating || _uiState.value is BackupUiState.Validating) return
        viewModelScope.launch {
            _events.emit(BackupUiEvent.LaunchFilePicker)
        }
    }

    fun onFileSelected(uri: String) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Creating
            val result = backupRepository.createBackup(uri)
            when (result) {
                is BackupResult.Success -> {
                    _uiState.value = BackupUiState.Success(result.manifest)
                }
                is BackupResult.Error -> {
                    _uiState.value = BackupUiState.Error(result)
                }
            }
        }
    }

    fun onPickerCancelled() {
        _uiState.value = BackupUiState.Idle
    }

    fun resetStatus() {
        _uiState.value = BackupUiState.Idle
    }
}
