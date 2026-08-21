package com.venkoi.cuentame.feature.waste.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.domain.repository.WasteFilter
import com.venkoi.cuentame.core.domain.repository.WasteSummary
import com.venkoi.cuentame.core.domain.usecase.ObserveWasteEventsUseCase
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
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
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

data class WasteListUiState(
    val isLoading: Boolean = true,
    val isSetupRequired: Boolean = false,
    val wasteEvents: List<WasteSummary> = emptyList(),
    val statusFilter: DocumentStatus? = null,
    val searchQuery: String = "",
    val currencyCode: String = "USD",
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

    val uiState: StateFlow<WasteListUiState> = restaurantRepository.observeRestaurant()
        .flatMapLatest { restaurant ->
            if (restaurant == null) {
                return@flatMapLatest flowOf(WasteListUiState(isLoading = false, isSetupRequired = true))
            }
            combine(
                _statusFilter,
                _searchQuery
            ) { status, query ->
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
                        searchQuery = query,
                        currencyCode = restaurant.currencyCode
                    )
                }.catch { e ->
                    emit(WasteListUiState(isLoading = false, error = e, currencyCode = restaurant.currencyCode))
                }
            }.flatMapLatest { it }
        }
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
