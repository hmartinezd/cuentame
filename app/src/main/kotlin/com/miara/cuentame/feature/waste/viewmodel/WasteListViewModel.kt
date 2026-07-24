package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.WasteFilter
import com.miara.cuentame.core.domain.repository.WasteSummary
import com.miara.cuentame.core.domain.usecase.ObserveWasteEventsUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WasteListUiState(
    val isLoading: Boolean = true,
    val wasteEvents: List<WasteSummary> = emptyList(),
    val statusFilter: DocumentStatus? = null,
    val searchQuery: String = "",
    val error: Throwable? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WasteListViewModel @Inject constructor(
    private val observeWasteEventsUseCase: ObserveWasteEventsUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<DocumentStatus?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<WasteListUiState> = combine(
        restaurantRepository.observeRestaurant(),
        _statusFilter,
        _searchQuery
    ) { restaurant, status, query ->
        if (restaurant == null) return@combine flowOf(WasteListUiState(isLoading = false))
        
        observeWasteEventsUseCase(
            WasteFilter(
                restaurantId = restaurant.id,
                status = status,
                query = query
            )
        ).map { events ->
            WasteListUiState(
                isLoading = false,
                wasteEvents = events,
                statusFilter = status,
                searchQuery = query
            )
        }
    }.flatMapLatest { it }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WasteListUiState()
    )

    fun onStatusFilterChanged(status: DocumentStatus?) {
        _statusFilter.value = status
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
