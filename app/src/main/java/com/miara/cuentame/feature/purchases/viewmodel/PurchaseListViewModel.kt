package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ObservePurchasesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
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

data class PurchaseListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val statusFilter: DocumentStatus? = null,
    val supplierFilter: SupplierId? = null,
    val purchases: List<PurchaseSummary> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val error: Throwable? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseListViewModel @Inject constructor(
    private val observePurchasesUseCase: ObservePurchasesUseCase,
    private val observeSuppliersUseCase: ObserveSuppliersUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<DocumentStatus?>(null)
    private val _supplierFilter = MutableStateFlow<SupplierId?>(null)

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val suppliers = restaurantIdFlow.flatMapLatest { rid ->
        observeSuppliersUseCase(rid, includeArchived = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<PurchaseListUiState> = combine(
        restaurantIdFlow.flatMapLatest { rid ->
            combine(_statusFilter, _supplierFilter, _searchQuery) { status, supplier, query ->
                PurchaseFilter(
                    restaurantId = rid,
                    status = status,
                    supplierId = supplier,
                    query = query.trim().ifBlank { null }
                )
            }.flatMapLatest { filter ->
                observePurchasesUseCase(filter)
            }
        },
        _searchQuery,
        _statusFilter,
        _supplierFilter,
        suppliers
    ) { purchases, query, status, supplierId, suppliers ->
        PurchaseListUiState(
            isLoading = false,
            searchQuery = query,
            statusFilter = status,
            supplierFilter = supplierId,
            purchases = purchases,
            suppliers = suppliers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChanged(status: DocumentStatus?) {
        _statusFilter.value = status
    }

    fun onSupplierFilterChanged(supplierId: SupplierId?) {
        _supplierFilter.value = supplierId
    }
}
