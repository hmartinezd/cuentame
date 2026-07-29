package com.miara.cuentame.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.DashboardSnapshot
import com.miara.cuentame.core.model.dashboard.MetricComparison
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
import com.miara.cuentame.feature.reports.ui.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

sealed interface ReportsScreenState {
    data object Loading : ReportsScreenState
    data object SetupRequired : ReportsScreenState
    data class Ready(
        val restaurantId: RestaurantId,
        val restaurantName: String,
        val currencyCode: String,
        val localeTag: String,
        val selectedRange: DashboardDateRange,
        val loadedRange: DashboardDateRange,
        val report: ReportsUiModel,
        val isRefreshing: Boolean = false,
        val refreshError: Boolean = false
    ) : ReportsScreenState
    data class Error(
        val selectedRange: DashboardDateRange,
        val cause: Throwable
    ) : ReportsScreenState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DashboardDateRange.LAST_30_DAYS)
    val selectedRange: StateFlow<DashboardDateRange> = _selectedRange.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ReportsScreenState> = combine(
        restaurantRepository.observeRestaurant(),
        _selectedRange,
        _retryTrigger
    ) { restaurant, range, _ ->
        restaurant to range
    }.flatMapLatest { (restaurant, range) ->
        if (restaurant == null) {
            flowOf(ReportsScreenState.SetupRequired)
        } else {
            dashboardRepository.observeDashboard(restaurant.id, range)
                .map { snapshot ->
                    ReportsScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        selectedRange = range,
                        loadedRange = range,
                        report = mapToUiModel(snapshot)
                    ) as ReportsScreenState
                }
                .onStart {
                    val current = uiState.value
                    if (current is ReportsScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(selectedRange = range, isRefreshing = true, refreshError = false))
                    } else {
                        emit(ReportsScreenState.Loading)
                    }
                }
                .catch { cause ->
                    val current = uiState.value
                    if (current is ReportsScreenState.Ready) {
                        emit(current.copy(selectedRange = range, isRefreshing = false, refreshError = true))
                    } else {
                        emit(ReportsScreenState.Error(range, cause))
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsScreenState.Loading
    )

    fun onRangeSelected(range: DashboardDateRange) {
        _selectedRange.value = range
    }

    fun onRetry() {
        _retryTrigger.value++
    }

    private fun mapToUiModel(snapshot: DashboardSnapshot): ReportsUiModel {
        val stockedCount = snapshot.inventory.stockedIngredientCount
        val valuedCount = snapshot.inventory.valuedIngredientCount
        val coverage = if (stockedCount > 0) {
            BigDecimal(valuedCount).divide(BigDecimal(stockedCount), 3, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP)
        } else null

        return ReportsUiModel(
            inventory = ReportsInventoryUiModel(
                totalValue = snapshot.inventory.totalValue,
                valuedIngredientCount = valuedCount,
                stockedIngredientCount = stockedCount,
                costCoverage = coverage,
                missingCostCount = snapshot.inventory.missingCostCount
            ),
            purchases = mapComparison(snapshot.purchases),
            waste = mapComparison(snapshot.waste),
            alerts = ReportsAlertsUiModel(
                negativeBalanceCount = snapshot.negativeBalanceCount,
                missingCostCount = snapshot.inventory.missingCostCount,
                missingOptionsCount = snapshot.activeIngredientsMissingOptionsCount
            ),
            counts = ReportsCountUiModel(
                completedCountCount = snapshot.completedCountCount,
                adjustedLineCount = snapshot.adjustedLineCount,
                mostRecentCompletedCountAt = snapshot.mostRecentCompletedCountAt
            ),
            topWasteItems = snapshot.topWasteItems
        )
    }

    private fun mapComparison(comparison: MetricComparison): DashboardMetricUiModel {
        val state = when {
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) > 0 -> MetricComparisonState.NEW
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) == 0 -> MetricComparisonState.NO_CHANGE
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
