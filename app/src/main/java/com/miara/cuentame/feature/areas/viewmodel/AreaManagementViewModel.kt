package com.miara.cuentame.feature.areas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ArchiveInventoryAreaUseCase
import com.miara.cuentame.core.domain.usecase.CreateInventoryAreaUseCase
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.domain.usecase.ReorderInventoryAreasUseCase
import com.miara.cuentame.core.domain.usecase.UpdateInventoryAreaUseCase
import com.miara.cuentame.core.model.inventory.InventoryArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AreaManagementUiState(
    val areas: List<InventoryArea> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: Throwable? = null
)

sealed interface AreaManagementEvent {
    data object OperationSuccess : AreaManagementEvent
}

@HiltViewModel
class AreaManagementViewModel @Inject constructor(
    observeInventoryAreasUseCase: ObserveInventoryAreasUseCase,
    private val createInventoryAreaUseCase: CreateInventoryAreaUseCase,
    private val updateInventoryAreaUseCase: UpdateInventoryAreaUseCase,
    private val archiveInventoryAreaUseCase: ArchiveInventoryAreaUseCase,
    private val reorderInventoryAreasUseCase: ReorderInventoryAreasUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<AreaManagementEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<AreaManagementUiState> = combine(
        observeInventoryAreasUseCase(),
        _isSaving,
        _error
    ) { areas, isSaving, error ->
        AreaManagementUiState(
            areas = areas,
            isLoading = false,
            isSaving = isSaving,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AreaManagementUiState()
    )

    fun onAddArea(name: String) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant() ?: return@launch
                val area = InventoryArea(
                    id = InventoryAreaId(idGenerator.newId()),
                    restaurantId = restaurant.id,
                    name = name,
                    normalizedName = "", 
                    sortOrder = uiState.value.areas.size,
                    isActive = true,
                    createdAt = timeProvider.now(),
                    updatedAt = timeProvider.now()
                )
                createInventoryAreaUseCase(area)
                _isSaving.value = false
                _events.send(AreaManagementEvent.OperationSuccess)
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onUpdateArea(area: InventoryArea) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                updateInventoryAreaUseCase(area.copy(updatedAt = timeProvider.now()))
                _isSaving.value = false
                _events.send(AreaManagementEvent.OperationSuccess)
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onArchiveArea(id: InventoryAreaId) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                archiveInventoryAreaUseCase(id, timeProvider.now())
                _isSaving.value = false
                _events.send(AreaManagementEvent.OperationSuccess)
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onMoveUp(index: Int) {
        if (_isSaving.value || index <= 0) return
        val list = uiState.value.areas.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        executeReorder(list)
    }

    fun onMoveDown(index: Int) {
        if (_isSaving.value || index >= uiState.value.areas.size - 1) return
        val list = uiState.value.areas.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        executeReorder(list)
    }

    private fun executeReorder(newList: List<InventoryArea>) {
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                reorderInventoryAreasUseCase(newList.map { it.id })
                _isSaving.value = false
                _events.send(AreaManagementEvent.OperationSuccess)
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
