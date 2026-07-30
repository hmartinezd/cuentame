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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@JvmInline
value class RestoreOperationId(val value: Long)

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

    companion object {
        private const val KEY_INSPECTION_ACTIVE = "inspection_active"
    }

    private val _uiState = MutableStateFlow<BackupRestoreUiState>(
        if (savedStateHandle.get<Boolean>(KEY_INSPECTION_ACTIVE) == true) {
            BackupRestoreUiState.Error(BackupRestoreFailure.OperationInterrupted)
        } else {
            BackupRestoreUiState.Idle
        }
    )
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val operationTokenGenerator = AtomicLong(0)
    private var activeOperationToken: Long = -1
    private var activeInspectionJob: Job? = null

    init {
        // Clear interruption marker after first state emission
        savedStateHandle[KEY_INSPECTION_ACTIVE] = false
    }

    fun onSelectFileClicked() {
        if (_uiState.value == BackupRestoreUiState.Inspecting) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onFileSelected(uri: String?) {
        if (uri == null) {
            _uiState.value = BackupRestoreUiState.Idle
            return
        }
        val source = BackupDocumentUri(uri)
        inspectArchive(source)
    }

    private fun inspectArchive(source: BackupDocumentUri) {
        cancelActiveOperation()
        val token = operationTokenGenerator.incrementAndGet()
        activeOperationToken = token
        
        _uiState.value = BackupRestoreUiState.Inspecting
        savedStateHandle[KEY_INSPECTION_ACTIVE] = true
        
        activeInspectionJob = viewModelScope.launch {
            try {
                val result = restoreRepository.inspect(source)
                if (activeOperationToken != token) return@launch
                
                _uiState.value = when (result) {
                    is BackupArchiveInspectionResult.Ready -> BackupRestoreUiState.PreviewReady(result.preview, source)
                    is BackupArchiveInspectionResult.Failure -> BackupRestoreUiState.Error(result.reason)
                }
            } catch (e: CancellationException) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupRestoreUiState.Idle
                }
                throw e
            } catch (e: Exception) {
                if (activeOperationToken == token) {
                    _uiState.value = BackupRestoreUiState.Error(BackupRestoreFailure.GenericIo)
                }
            } finally {
                if (activeOperationToken == token) {
                    savedStateHandle[KEY_INSPECTION_ACTIVE] = false
                }
            }
        }
    }

    fun onDismissRequest() {
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.Idle
    }

    private fun cancelActiveOperation() {
        activeOperationToken = -1
        activeInspectionJob?.cancel()
        savedStateHandle[KEY_INSPECTION_ACTIVE] = false
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveOperation()
    }
}
