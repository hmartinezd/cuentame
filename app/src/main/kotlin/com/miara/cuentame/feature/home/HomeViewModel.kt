package com.miara.cuentame.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data object SetupRequired : HomeScreenState
    data class Ready(
        val restaurantId: RestaurantId,
        val restaurantName: String,
        val currencyCode: String,
        val localeTag: String,
        val selectedRange: DashboardDateRange,
        val loadedRange: DashboardDateRange,
        val dashboard: DashboardUiModel,
        val isRefreshing: Boolean = false,
        val refreshError: Boolean = false
    ) : HomeScreenState
    data class Error(
        val selectedRange: DashboardDateRange,
        val cause: Throwable
    ) : HomeScreenState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DashboardDateRange.LAST_30_DAYS)
    val selectedRange: StateFlow<DashboardDateRange> = _selectedRange.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeScreenState> = combine(
        restaurantRepository.observeRestaurant(),
        _selectedRange,
        _retryTrigger
    ) { restaurant, range, _ ->
        restaurant to range
    }.flatMapLatest { (restaurant, range) ->
        if (restaurant == null) {
            flowOf(HomeScreenState.SetupRequired)
        } else {
            dashboardRepository.observeDashboard(restaurant.id, range)
                .map { snapshot ->
                    HomeScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        selectedRange = range,
                        loadedRange = range,
                        dashboard = mapToUiModel(snapshot)
                    ) as HomeScreenState
                }
                .onStart {
                    val current = uiState.value
                    if (current is HomeScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(selectedRange = range, isRefreshing = true, refreshError = false))
                    } else {
                        emit(HomeScreenState.Loading)
                    }
                }
                .catch { cause ->
                    val current = uiState.value
                    if (current is HomeScreenState.Ready) {
                        emit(current.copy(selectedRange = range, isRefreshing = false, refreshError = true))
                    } else {
                        emit(HomeScreenState.Error(range, cause))
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeScreenState.Loading
    )

    fun onRangeSelected(range: DashboardDateRange) {
        _selectedRange.value = range
    }

    fun onRetry() {
        _retryTrigger.value++
    }

    private fun mapToUiModel(snapshot: DashboardSnapshot): DashboardUiModel {
        val stockedCount = snapshot.inventory.stockedIngredientCount
        val valuedCount = snapshot.inventory.valuedIngredientCount
        // Use BigDecimal for coverage calculation to avoid Double precision issues
        val coverage = if (stockedCount > 0) {
            BigDecimal(valuedCount).divide(BigDecimal(stockedCount), 3, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(1, java.math.RoundingMode.HALF_UP)
        } else null

        return DashboardUiModel(
            inventoryValue = snapshot.inventory.totalValue,
            valuedIngredientCount = valuedCount,
            stockedIngredientCount = stockedCount,
            costCoverage = coverage,
            missingCostCount = snapshot.inventory.missingCostCount,
            missingOptionsCount = snapshot.activeIngredientsMissingOptionsCount,
            purchaseSpend = mapComparison(snapshot.purchases),
            wasteValue = mapComparison(snapshot.waste),
            negativeBalanceCount = snapshot.negativeBalanceCount,
            completedCountCount = snapshot.completedCountCount,
            mostRecentCompletedCountAt = snapshot.mostRecentCompletedCountAt,
            adjustedLineCount = snapshot.adjustedLineCount,
            topWasteItems = snapshot.topWasteItems,
            recentActivity = snapshot.recentActivity
        )
    }

    private fun mapComparison(comparison: MetricComparison): DashboardMetricUiModel {
        val state = when {
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) > 0 -> 
                MetricComparisonState.NEW
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) == 0 -> 
                MetricComparisonState.NO_CHANGE
            comparison.percentageChange == null -> MetricComparisonState.UNAVAILABLE
            comparison.percentageChange.compareTo(BigDecimal.ZERO) > 0 -> MetricComparisonState.INCREASE
            comparison.percentageChange.compareTo(BigDecimal.ZERO) < 0 -> MetricComparisonState.DECREASE
            else -> MetricComparisonState.NO_CHANGE
        }
        
        return DashboardMetricUiModel(
            value = comparison.current,
            previousValue = comparison.previous,
            absoluteChange = comparison.absoluteChange,
            percentageChange = comparison.percentageChange,
            comparisonState = state
        )
    }
}
