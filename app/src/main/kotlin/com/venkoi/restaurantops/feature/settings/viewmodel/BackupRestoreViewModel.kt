package com.venkoi.restaurantops.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.model.backup.BackupRestoreEligibility
import com.venkoi.restaurantops.core.model.backup.BackupRestoreFailure
import com.venkoi.restaurantops.core.model.backup.BackupRestorePreview
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
    data object RecoveryInProgress : BackupRestoreUiState

    data class PreviewReady(
        val preview: BackupRestorePreview,
        val eligibility: BackupRestoreEligibility
    ) : BackupRestoreUiState

    data class ConfirmingRestore(
        val preview: BackupRestorePreview
    ) : BackupRestoreUiState

    data class Applying(
        val progress: BackupRestoreProgress
    ) : BackupRestoreUiState

    data class Success(
        val summary: BackupRestoreSuccessSummary
    ) : BackupRestoreUiState

    data object RecoverySuccess : BackupRestoreUiState

    data class Error(
        val reason: BackupRestoreFailure,
        val canChooseAnotherFile: Boolean = true
    ) : BackupRestoreUiState

    data object RecoveryRequired : BackupRestoreUiState
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
    private var lastInspectedPreview: BackupRestorePreview? = null

    init {
        observeStartupState()
        checkInterruptedOperation()
    }

    private fun observeStartupState() {
        viewModelScope.launch {
            restoreCoordinator.startupState.collect { state ->
                handleStartupState(state)
            }
        }
    }

    private fun checkInterruptedOperation() {
        if (savedStateHandle.get<Boolean>(KEY_INSPECTION_ACTIVE) == true) {
            _uiState.value = BackupRestoreUiState.Error(
                reason = BackupRestoreFailure.OperationInterrupted,
                canChooseAnotherFile = true
            )
        }
        savedStateHandle[KEY_INSPECTION_ACTIVE] = false
    }

    private fun handleStartupState(state: RestoreStartupState) {
        when (state) {
            RestoreStartupState.NotStarted,
            RestoreStartupState.Recovering -> {
                _uiState.value = BackupRestoreUiState.RecoveryInProgress
            }
            RestoreStartupState.Ready -> {
                if (_uiState.value is BackupRestoreUiState.RecoveryRequired || 
                    _uiState.value is BackupRestoreUiState.RecoveryInProgress) {
                    _uiState.value = BackupRestoreUiState.Idle
                }
            }
            is RestoreStartupState.Recovered -> {
                _uiState.value = BackupRestoreUiState.RecoverySuccess
            }
            RestoreStartupState.RecoveryRequired -> {
                _uiState.value = BackupRestoreUiState.RecoveryRequired
            }
        }
    }


    fun onSelectFileClicked() {
        if (isBlocked()) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onFileSelected(uri: String?) {
        if (isBlocked()) return
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
                        lastInspectedPreview = result.preview
                        BackupRestoreUiState.PreviewReady(result.preview, result.eligibility)
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
        if (isBlocked()) return
        val preview = lastInspectedPreview ?: return
        
        val state = _uiState.value
        if (state is BackupRestoreUiState.PreviewReady && state.eligibility != BackupRestoreEligibility.Eligible) {
             return
        }

        _uiState.value = BackupRestoreUiState.ConfirmingRestore(preview)
    }

    fun onRestoreConfirmationCancelled() {
        if (isBlocked()) return
        val preview = lastInspectedPreview ?: return
        
        val currentArchive = lastInspectedArchive ?: return
        val eligibility = if (currentArchive.manifest.attachments.isNotEmpty()) {
            BackupRestoreEligibility.AttachmentsNotSupported
        } else {
            BackupRestoreEligibility.Eligible
        }
        _uiState.value = BackupRestoreUiState.PreviewReady(preview, eligibility)
    }

    fun onRestoreConfirmed() {
        if (isBlocked()) return
        val archive = lastInspectedArchive ?: return
        val preview = lastInspectedPreview ?: return
        
        val token = operationTokenGenerator.incrementAndGet()
        activeOperationToken = token
        
        _uiState.value = BackupRestoreUiState.Applying(BackupRestoreProgress.ValidatingBackup)
        
        activeJob = viewModelScope.launch {
            try {
                val result = restoreCoordinator.apply(archive.source, archive.fingerprint) { progress ->
                    _uiState.value = BackupRestoreUiState.Applying(progress)
                }
                if (activeOperationToken != token) return@launch
                
                _uiState.value = when (result) {
                    is BackupRestoreApplyResult.Success -> {
                        lastInspectedArchive = null
                        lastInspectedPreview = null
                        BackupRestoreUiState.Success(
                            BackupRestoreSuccessSummary(
                                restaurantName = archive.manifest.restaurantName ?: "",
                                recordCount = preview.totalRecordCount
                            )
                        )
                    }
                    is BackupRestoreApplyResult.Failure -> {
                        if (result.reason == BackupRestoreFailure.RecoveryRequired) {
                            BackupRestoreUiState.RecoveryRequired
                        } else {
                            BackupRestoreUiState.Error(
                                reason = result.reason,
                                canChooseAnotherFile = true
                            )
                        }
                    }
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

    fun onRetryRecoveryClicked() {
        if (_uiState.value != BackupRestoreUiState.RecoveryRequired) return
        _uiState.value = BackupRestoreUiState.RecoveryInProgress
        viewModelScope.launch {
            restoreCoordinator.retryRecovery()
        }
    }

    fun onChooseAnotherClicked() {
        if (isBlocked()) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.SelectingFile
    }

    fun onDismissRequest() {
        if (isBlocked()) return
        if (_uiState.value is BackupRestoreUiState.RecoveryRequired) return
        cancelActiveOperation()
        _uiState.value = BackupRestoreUiState.Idle
    }

    private fun cancelActiveOperation() {
        activeOperationToken = -1
        activeJob?.cancel()
        savedStateHandle[KEY_INSPECTION_ACTIVE] = false
        lastInspectedArchive = null
        lastInspectedPreview = null
    }

    private fun isBlocked(): Boolean {
        val state = _uiState.value
        return state is BackupRestoreUiState.Applying || 
               state is BackupRestoreUiState.RecoveryRequired ||
               state is BackupRestoreUiState.RecoveryInProgress
    }

    override fun onCleared() {
        super.onCleared()
        if (!isBlocked()) {
            cancelActiveOperation()
        }
    }
}
