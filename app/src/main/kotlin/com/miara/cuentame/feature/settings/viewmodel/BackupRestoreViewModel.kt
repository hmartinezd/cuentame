package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupRestoreUiState {
    data object Idle : BackupRestoreUiState
    data object SelectingFile : BackupRestoreUiState
    data object Inspecting : BackupRestoreUiState

    data class PreviewReady(
        val preview: BackupRestorePreview,
        val source: BackupDocumentUri
    ) : BackupRestoreUiState

    data class Error(
        val reason: BackupRestoreFailure
    ) : BackupRestoreUiState
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val restoreRepository: BackupRestoreRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private var activeInspectionJob: Job? = null

    fun onSelectFileClicked() {
        if (_uiState.value == BackupRestoreUiState.Inspecting) return
        activeInspectionJob?.cancel()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onFileSelected(uri: String) {
        val source = BackupDocumentUri(uri)
        inspectArchive(source)
    }

    private fun inspectArchive(source: BackupDocumentUri) {
        activeInspectionJob?.cancel()
        _uiState.value = BackupRestoreUiState.Inspecting
        
        activeInspectionJob = viewModelScope.launch {
            try {
                val result = restoreRepository.inspect(source)
                _uiState.value = when (result) {
                    is BackupArchiveInspectionResult.Ready -> BackupRestoreUiState.PreviewReady(result.preview, source)
                    is BackupArchiveInspectionResult.Failure -> BackupRestoreUiState.Error(result.reason)
                }
            } catch (e: CancellationException) {
                // Keep Inspecting or move to Idle? Plan says cancellation is not shown as an error.
                if (_uiState.value == BackupRestoreUiState.Inspecting) {
                    _uiState.value = BackupRestoreUiState.Idle
                }
                throw e
            } catch (e: Exception) {
                _uiState.value = BackupRestoreUiState.Error(BackupRestoreFailure.GenericIo)
            }
        }
    }

    fun onDismissRequest() {
        activeInspectionJob?.cancel()
        _uiState.value = BackupRestoreUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        activeInspectionJob?.cancel()
    }
}
