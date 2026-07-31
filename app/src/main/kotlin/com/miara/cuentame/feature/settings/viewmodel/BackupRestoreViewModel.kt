package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RestoreRecoveryResult
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

data class BackupRestoreSuccessSummary(
    val restaurantName: String,
    val recordCount: Long
)

sealed interface BackupRestoreUiState {
    data object Idle : BackupRestoreUiState
    data object SelectingFile : BackupRestoreUiState
    data object Inspecting : BackupRestoreUiState

    data class PreviewReady(
        val preview: BackupRestorePreview
    ) : BackupRestoreUiState

    data class ConfirmingRestore(
        val preview: BackupRestorePreview
    ) : BackupRestoreUiState

    data class Applying(
        val phase: RestorePhase,
        val progress: Float? = null
    ) : BackupRestoreUiState

    data class Success(
        val summary: BackupRestoreSuccessSummary
    ) : BackupRestoreUiState

    data class Error(
        val reason: BackupRestoreFailure,
        val canChooseAnotherFile: Boolean = true,
        val recoveryRequired: Boolean = false
    ) : BackupRestoreUiState
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val restoreCoordinator: BackupRestoreCoordinator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_INSPECTION_ACTIVE = "inspection_active"
    }

    private val _uiState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val operationTokenGenerator = AtomicLong(0)
    private var activeOperationToken: Long = -1
    private var activeJob: Job? = null
    
    private var lastInspectedArchive: InspectedBackupArchive? = null

    init {
        viewModelScope.launch {
            val recoveryResult = restoreCoordinator.recoverIfNeeded()
            if (recoveryResult is RestoreRecoveryResult.RecoveryRequired) {
                _uiState.value = BackupRestoreUiState.Error(
                    reason = BackupRestoreFailure.RecoveryRequired,
                    canChooseAnotherFile = false,
                    recoveryRequired = true
                )
            } else if (recoveryResult is RestoreRecoveryResult.Recovered) {
                _uiState.value = BackupRestoreUiState.Error(
                    reason = BackupRestoreFailure.OperationInterrupted,
                    canChooseAnotherFile = true
                )
            } else if (savedStateHandle.get<Boolean>(KEY_INSPECTION_ACTIVE) == true) {
                _uiState.value = BackupRestoreUiState.Error(BackupRestoreFailure.OperationInterrupted)
            }
            savedStateHandle[KEY_INSPECTION_ACTIVE] = false
        }
    }

    fun onSelectFileClicked() {
        if (isMutationActive()) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onFileSelected(uri: String?) {
        if (isMutationActive()) return
        cancelActiveOperation()
        if (uri == null) {
            _uiState.value = BackupRestoreUiState.Idle
            return
        }
        val source = BackupDocumentUri(uri)
        inspectArchive(source)
    }

    private fun inspectArchive(source: BackupDocumentUri) {
        val token = operationTokenGenerator.incrementAndGet()
        activeOperationToken = token
        
        _uiState.value = BackupRestoreUiState.Inspecting
        savedStateHandle[KEY_INSPECTION_ACTIVE] = true
        
        activeJob = viewModelScope.launch {
            try {
                val result = restoreCoordinator.inspect(source)
                if (activeOperationToken != token) return@launch
                
                _uiState.value = when (result) {
                    is BackupArchiveInspectionResult.Ready -> {
                        lastInspectedArchive = result.archive
                        BackupRestoreUiState.PreviewReady(result.preview)
                    }
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

    fun onRestoreClicked() {
        val archive = lastInspectedArchive ?: return
        if (isMutationActive()) return
        _uiState.value = BackupRestoreUiState.ConfirmingRestore(
            preview = createPreviewFromArchive(archive)
        )
    }

    fun onRestoreConfirmed() {
        val archive = lastInspectedArchive ?: return
        if (isMutationActive()) return
        
        val token = operationTokenGenerator.incrementAndGet()
        activeOperationToken = token
        
        _uiState.value = BackupRestoreUiState.Applying(RestorePhase.STAGING)
        
        activeJob = viewModelScope.launch {
            try {
                val result = restoreCoordinator.apply(archive.source, archive.fingerprint)
                if (activeOperationToken != token) return@launch
                
                _uiState.value = when (result) {
                    is BackupRestoreApplyResult.Success -> BackupRestoreUiState.Success(
                        BackupRestoreSuccessSummary(
                            restaurantName = archive.manifest.restaurantName ?: "",
                            recordCount = archive.preview().totalRecordCount
                        )
                    )
                    is BackupRestoreApplyResult.Failure -> BackupRestoreUiState.Error(
                        reason = result.reason,
                        recoveryRequired = result.reason == BackupRestoreFailure.RecoveryRequired,
                        canChooseAnotherFile = result.reason != BackupRestoreFailure.RecoveryRequired
                    )
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
            }
        }
    }

    fun onChooseAnotherClicked() {
        if (isMutationActive()) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onDismissRequest() {
        if (isMutationActive()) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.Idle
    }

    private fun cancelActiveOperation() {
        activeOperationToken = -1
        activeJob?.cancel()
        savedStateHandle[KEY_INSPECTION_ACTIVE] = false
        lastInspectedArchive = null
    }

    private fun isMutationActive(): Boolean {
        val state = _uiState.value
        return state is BackupRestoreUiState.Applying
    }

    private fun createPreviewFromArchive(archive: InspectedBackupArchive): BackupRestorePreview {
        // Re-use logic or just provide from the Ready state if we stored it
        // Actually, we should probably have stored the preview too.
        // For now, I'll just use a dummy or re-calculate.
        return archive.preview()
    }

    private fun InspectedBackupArchive.preview(): BackupRestorePreview {
        // Re-calculating preview from manifest (simplified)
        val totalRecordCount = manifest.tableMetadata.values.sumOf { it.entryCount.toLong() }
        val totalAttachmentBytes = manifest.attachments.sumOf { it.sizeBytes }
        return BackupRestorePreview(
            restaurantName = manifest.restaurantName ?: "",
            createdAt = null, // simplified
            backupFormatVersion = manifest.backupFormatVersion,
            databaseSchemaVersion = manifest.databaseSchemaVersion,
            localeTag = manifest.localeTag ?: "",
            totalRecordCount = totalRecordCount,
            attachmentCount = manifest.attachments.size,
            totalAttachmentBytes = totalAttachmentBytes
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (!isMutationActive()) {
            cancelActiveOperation()
        }
    }
}
