package com.miara.cuentame.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data object SetupRequired : HomeScreenState
    data class Ready(
        val restaurantName: String,
        val currencyCode: String,
        val selectedRange: DashboardDateRange,
        val dashboard: DashboardUiModel
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
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        selectedRange = range,
                        dashboard = mapToUiModel(snapshot)
                    ) as HomeScreenState
                }
                .onStart { emit(HomeScreenState.Loading) }
                .catch { emit(HomeScreenState.Error(range, it)) }
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
        return DashboardUiModel(
            inventoryValue = snapshot.inventory.totalValue,
            costCoverage = if (snapshot.inventory.stockedIngredientCount > 0) {
                snapshot.inventory.valuedIngredientCount.toDouble() / snapshot.inventory.stockedIngredientCount
            } else null,
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
