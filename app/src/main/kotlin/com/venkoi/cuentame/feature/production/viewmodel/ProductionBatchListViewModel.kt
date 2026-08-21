package com.venkoi.cuentame.feature.production.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.core.domain.repository.ProductionBatchRepository
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.ProductionBatchSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductionBatchListUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val batches: List<ProductionBatchSummary> = emptyList(),
    val currencyCode: String = "",
    val searchQuery: String = "",
    val selectedStatus: DocumentStatus? = null
)

@HiltViewModel
class ProductionBatchListViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionBatchListUiState())
    val uiState: StateFlow<ProductionBatchListUiState> = _uiState.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    init {
        observeBatches()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBatches() {
        viewModelScope.launch {
            retryTrigger.collectLatest {
                _uiState.update { it.copy(screenState = ProductionBatchScreenState.Loading) }
                try {
                    val restaurant = restaurantRepository.getRestaurant()
                    if (restaurant == null) {
                        _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(com.venkoi.cuentame.core.presentation.ui.UiMessage.Resource(com.venkoi.cuentame.R.string.error_no_restaurant))) }
                        return@collectLatest
                    }

                    combine(
                        _uiState.map { it.searchQuery }.distinctUntilChanged(),
                        _uiState.map { it.selectedStatus }.distinctUntilChanged()
                    ) { query, status ->
                        query to status
                    }.flatMapLatest { (query, status) ->
                        productionBatchRepository.observeBatches(restaurant.id, status)
                            .map { batches ->
                                if (query.isBlank()) batches
                                else batches.filter { 
                                    it.recipeName.contains(query, ignoreCase = true) ||
                                    it.outputIngredientName.contains(query, ignoreCase = true)
                                }
                            }
                    }.collectLatest { filteredBatches ->
                        _uiState.update {
                            it.copy(
                                screenState = ProductionBatchScreenState.Ready,
                                batches = filteredBatches,
                                currencyCode = restaurant.currencyCode
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            screenState = ProductionBatchScreenState.LoadError(com.venkoi.cuentame.core.presentation.ui.UiMessage.Resource(com.venkoi.cuentame.R.string.error_generic))
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChanged(status: DocumentStatus?) {
        _uiState.update { it.copy(selectedStatus = status) }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }
}
