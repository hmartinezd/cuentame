package com.miara.cuentame.feature.suppliers.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.model.supplier.Supplier
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

data class SupplierListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val suppliers: List<Supplier> = emptyList(),
    val error: Throwable? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupplierListViewModel @Inject constructor(
    private val observeSuppliersUseCase: ObserveSuppliersUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showArchived = MutableStateFlow(false)

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val uiState: StateFlow<SupplierListUiState> = combine(
        combine(restaurantIdFlow, _showArchived) { rid, archived -> rid to archived }
            .flatMapLatest { (rid, archived) -> observeSuppliersUseCase(rid, archived) },
        _searchQuery,
        _showArchived
    ) { suppliers, query, showArchived ->
        val filtered = suppliers.filter { 
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }
        
        SupplierListUiState(
            isLoading = false,
            searchQuery = query,
            showArchived = showArchived,
            suppliers = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SupplierListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onShowArchivedToggled(show: Boolean) {
        _showArchived.value = show
    }
}
