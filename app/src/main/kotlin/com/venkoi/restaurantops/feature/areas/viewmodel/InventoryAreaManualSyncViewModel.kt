package com.venkoi.restaurantops.feature.areas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreview
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreviewLoader
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreviewResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictRef
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictResolutionResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictResolver
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncService
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class InventoryAreaConflictMessage { REMOTE_FAILURE, LOCAL_CONSTRAINT, GENERIC_FAILURE }

sealed interface InventoryAreaManualSyncUiState {
    data object Idle : InventoryAreaManualSyncUiState
    data object Syncing : InventoryAreaManualSyncUiState
    data object Success : InventoryAreaManualSyncUiState
    data object RemoteFailure : InventoryAreaManualSyncUiState
    data object Error : InventoryAreaManualSyncUiState
    data class LoadingConflict(val conflict: InventoryAreaConflictRef) : InventoryAreaManualSyncUiState
    data class PreviewUnavailable(
        val conflict: InventoryAreaConflictRef,
        val local: com.venkoi.restaurantops.core.model.inventory.InventoryArea?,
        val remoteFailure: Boolean
    ) : InventoryAreaManualSyncUiState
    data class Conflict(
        val conflict: InventoryAreaConflictRef,
        val preview: InventoryAreaConflictPreview,
        val isResolving: Boolean = false,
        val message: InventoryAreaConflictMessage? = null,
        val conflictingEntityId: String? = null
    ) : InventoryAreaManualSyncUiState
}

@HiltViewModel
class InventoryAreaManualSyncViewModel @Inject constructor(
    private val restaurants: RestaurantRepository,
    private val syncService: InventoryAreaSyncService,
    private val resolver: InventoryAreaConflictResolver,
    private val previewLoader: InventoryAreaConflictPreviewLoader,
    private val mutationGate: InventoryAreaMutationGate
) : ViewModel() {
    private val _uiState = MutableStateFlow<InventoryAreaManualSyncUiState>(InventoryAreaManualSyncUiState.Idle)
    val uiState: StateFlow<InventoryAreaManualSyncUiState> = _uiState.asStateFlow()
    private var operation: Job? = null

    fun syncNow() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { runSafely { runFreshSync() } }
    }

    fun retryPreview() {
        val state = _uiState.value as? InventoryAreaManualSyncUiState.PreviewUnavailable ?: return
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { runSafely { loadPreview(state.conflict) } }
    }

    fun useThisDevice() = resolve(keepLocal = true)

    fun useCloudVersion() = resolve(keepLocal = false)

    fun dismissConflict() {
        if (operation?.isActive == true) return
        if (_uiState.value is InventoryAreaManualSyncUiState.Conflict ||
            _uiState.value is InventoryAreaManualSyncUiState.PreviewUnavailable
        ) {
            mutationGate.unlockConflict()
            _uiState.value = InventoryAreaManualSyncUiState.Idle
        }
    }

    fun clearResult() {
        if (_uiState.value is InventoryAreaManualSyncUiState.Success ||
            _uiState.value is InventoryAreaManualSyncUiState.RemoteFailure ||
            _uiState.value is InventoryAreaManualSyncUiState.Error
        ) _uiState.value = InventoryAreaManualSyncUiState.Idle
    }

    private fun resolve(keepLocal: Boolean) {
        val state = _uiState.value as? InventoryAreaManualSyncUiState.Conflict ?: return
        if (state.isResolving || operation?.isActive == true) return
        operation = viewModelScope.launch { runSafely {
            _uiState.value = state.copy(isResolving = true, message = null)
            val result = if (keepLocal) resolver.resolveKeepLocal(state.conflict)
            else resolver.resolveUseCloud(state.conflict)
            when (result) {
                is InventoryAreaConflictResolutionResult.KeepLocalPrepared,
                is InventoryAreaConflictResolutionResult.CloudAccepted -> continueAfterResolution()
                InventoryAreaConflictResolutionResult.StaleConflict -> continueAfterResolution()
                InventoryAreaConflictResolutionResult.RemoteFailure ->
                    _uiState.value = state.copy(message = InventoryAreaConflictMessage.REMOTE_FAILURE)
                InventoryAreaConflictResolutionResult.ProtocolFailure ->
                    _uiState.value = state.copy(message = InventoryAreaConflictMessage.GENERIC_FAILURE)
                is InventoryAreaConflictResolutionResult.LocalConstraintConflict ->
                    _uiState.value = state.copy(
                        message = InventoryAreaConflictMessage.LOCAL_CONSTRAINT,
                        conflictingEntityId = result.conflictingEntityId
                    )
            }
        } }
    }

    private suspend fun runSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutationGate.unlockConflict()
            _uiState.value = InventoryAreaManualSyncUiState.Error
        }
    }

    private suspend fun runFreshSync() {
        _uiState.value = InventoryAreaManualSyncUiState.Syncing
        val restaurant = restaurants.getRestaurant()
        if (restaurant == null) {
            _uiState.value = InventoryAreaManualSyncUiState.Error
            return
        }
        when (val result = syncService.sync(restaurant.id)) {
            is InventoryAreaSyncResult.Success -> _uiState.value = InventoryAreaManualSyncUiState.Success
            is InventoryAreaSyncResult.Conflict -> {
                val conflict = InventoryAreaConflictRef(restaurant.id, result.entityId, result.operationId)
                mutationGate.lockForConflict()
                _uiState.value = InventoryAreaManualSyncUiState.LoadingConflict(conflict)
                mutationGate.awaitAcceptedMutations()
                loadPreview(conflict)
            }
            InventoryAreaSyncResult.RemoteFailure -> _uiState.value = InventoryAreaManualSyncUiState.RemoteFailure
            else -> _uiState.value = InventoryAreaManualSyncUiState.Error
        }
    }

    private suspend fun loadPreview(conflict: InventoryAreaConflictRef) {
        _uiState.value = InventoryAreaManualSyncUiState.LoadingConflict(conflict)
        _uiState.value = when (val result = previewLoader.load(conflict)) {
            is InventoryAreaConflictPreviewResult.Available ->
                InventoryAreaManualSyncUiState.Conflict(conflict, result.preview)
            is InventoryAreaConflictPreviewResult.RemoteFailure ->
                InventoryAreaManualSyncUiState.PreviewUnavailable(conflict, result.local, remoteFailure = true)
            is InventoryAreaConflictPreviewResult.Unavailable ->
                InventoryAreaManualSyncUiState.PreviewUnavailable(conflict, result.local, remoteFailure = false)
        }
    }

    private suspend fun continueAfterResolution() {
        mutationGate.unlockConflict()
        runFreshSync()
    }

    override fun onCleared() {
        mutationGate.unlockConflict()
        super.onCleared()
    }
}
