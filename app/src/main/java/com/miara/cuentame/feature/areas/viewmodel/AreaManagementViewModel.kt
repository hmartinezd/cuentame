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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AreaManagementUiState(
    val areas: List<InventoryArea> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)

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

    val uiState: StateFlow<AreaManagementUiState> = observeInventoryAreasUseCase()
        .map { AreaManagementUiState(areas = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AreaManagementUiState()
        )

    fun onAddArea(name: String) {
        viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant() ?: return@launch
                val area = InventoryArea(
                    id = InventoryAreaId(idGenerator.newId()),
                    restaurantId = restaurant.id,
                    name = name,
                    normalizedName = "", // Repository handles normalization
                    sortOrder = uiState.value.areas.size,
                    isActive = true,
                    createdAt = timeProvider.now(),
                    updatedAt = timeProvider.now()
                )
                createInventoryAreaUseCase(area)
            } catch (e: Exception) {
                // TODO: handle error in state
            }
        }
    }

    fun onUpdateArea(area: InventoryArea) {
        viewModelScope.launch {
            try {
                updateInventoryAreaUseCase(area.copy(updatedAt = timeProvider.now()))
            } catch (e: Exception) {
                // TODO
            }
        }
    }

    fun onArchiveArea(id: InventoryAreaId) {
        viewModelScope.launch {
            try {
                archiveInventoryAreaUseCase(id, timeProvider.now())
            } catch (e: Exception) {
                // TODO
            }
        }
    }

    fun onMoveUp(index: Int) {
        if (index <= 0) return
        val list = uiState.value.areas.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        viewModelScope.launch {
            reorderInventoryAreasUseCase(list.map { it.id })
        }
    }

    fun onMoveDown(index: Int) {
        if (index >= uiState.value.areas.size - 1) return
        val list = uiState.value.areas.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        viewModelScope.launch {
            reorderInventoryAreasUseCase(list.map { it.id })
        }
    }
}
