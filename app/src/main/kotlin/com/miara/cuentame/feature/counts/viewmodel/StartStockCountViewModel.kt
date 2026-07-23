package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.model.inventory.InventoryArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StartStockCountUiState(
    val isLoading: Boolean = true,
    val isStarting: Boolean = false,
    val name: String = "",
    val effectiveAt: Instant = Instant.now(),
    val availableAreas: List<InventoryArea> = emptyList(),
    val selectedAreaIds: Set<InventoryAreaId> = emptySet(),
    val notes: String = "",
    val error: Throwable? = null
)

sealed interface StartStockCountEvent {
    data class Success(val countId: StockCountId) : StartStockCountEvent
}

@HiltViewModel
class StartStockCountViewModel @Inject constructor(
    private val repository: StockCountRepository,
    private val observeInventoryAreasUseCase: ObserveInventoryAreasUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartStockCountUiState(effectiveAt = timeProvider.now()))
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<StartStockCountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val areasFlow = observeInventoryAreasUseCase(activeOnly = true)
    private val restaurantFlow = restaurantRepository.observeRestaurant().filterNotNull()

    init {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())
        val defaultName = "Count ${dateFormatter.format(timeProvider.now())}"
        
        viewModelScope.launch {
            areasFlow.collect { areas ->
                _uiState.update { it.copy(
                    isLoading = false,
                    availableAreas = areas,
                    name = if (it.name.isBlank()) defaultName else it.name
                ) }
            }
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name) }
    
    fun onDateChanged(date: Instant) {
        _uiState.update { it.copy(effectiveAt = date) }
    }

    fun onAreaToggle(areaId: InventoryAreaId) {
        _uiState.update { state ->
            val newSelection = if (state.selectedAreaIds.contains(areaId)) {
                state.selectedAreaIds - areaId
            } else {
                state.selectedAreaIds + areaId
            }
            state.copy(selectedAreaIds = newSelection)
        }
    }

    fun onNotesChanged(notes: String) = _uiState.update { it.copy(notes = notes) }

    fun onStart() {
        val state = _uiState.value
        if (state.isStarting || state.name.isBlank() || state.selectedAreaIds.isEmpty()) return

        _uiState.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            try {
                android.util.Log.d("StartStockCount", "Starting count with name: ${state.name}, areas: ${state.selectedAreaIds}")
                val restaurant = restaurantRepository.getRestaurant() ?: throw Exception("No restaurant")
                val countId = repository.start(
                    StartStockCountCommand(
                        restaurantId = restaurant.id,
                        name = state.name,
                        effectiveAt = state.effectiveAt,
                        areaIds = state.selectedAreaIds.toList(),
                        notes = state.notes.ifBlank { null }
                    )
                )
                android.util.Log.d("StartStockCount", "Count started successfully: $countId")
                _events.send(StartStockCountEvent.Success(countId))
            } catch (e: Exception) {
                android.util.Log.e("StartStockCount", "Error starting count", e)
                _uiState.update { it.copy(isStarting = false, error = e) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
