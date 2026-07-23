package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StockCountFilter
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.StockCountSummary
import com.miara.cuentame.core.model.inventory.StockCountStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StockCountListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val statusFilter: StockCountStatus? = null,
    val counts: List<StockCountSummary> = emptyList(),
    val error: Throwable? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockCountListViewModel @Inject constructor(
    private val repository: StockCountRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<StockCountStatus?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val uiState: StateFlow<StockCountListUiState> = combine(
        restaurantIdFlow.flatMapLatest { rid ->
            combine(_statusFilter, _searchQuery) { status, query ->
                StockCountFilter(
                    restaurantId = rid,
                    status = status,
                    query = query.trim().ifBlank { null }
                )
            }.flatMapLatest { filter ->
                repository.observeCounts(filter)
            }
        },
        _searchQuery,
        _statusFilter,
        _error
    ) { counts, query, status, error ->
        StockCountListUiState(
            isLoading = false,
            searchQuery = query,
            statusFilter = status,
            counts = counts,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockCountListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChanged(status: StockCountStatus?) {
        _statusFilter.value = status
    }

    fun clearError() {
        _error.value = null
    }
}
