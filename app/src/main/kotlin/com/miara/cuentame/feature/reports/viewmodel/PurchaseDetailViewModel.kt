package com.miara.cuentame.feature.reports.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.PurchaseDetailReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val restaurantRepository: RestaurantRepository,
    private val detailedReportsRepository: DetailedReportsRepository,
    private val periodCalculator: ReportingPeriodCalculator
) : ViewModel() {

    private val initialRange: DashboardDateRange = savedStateHandle.get<String>("range")
        ?.let { try { DashboardDateRange.valueOf(it) } catch (_: Exception) { null } }
        ?: DashboardDateRange.LAST_30_DAYS

    private val _selectedRange = MutableStateFlow(initialRange)
    val selectedRange: StateFlow<DashboardDateRange> = _selectedRange.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DetailReportScreenState<PurchaseDetailReport>> = combine(
        restaurantRepository.observeRestaurant(),
        _selectedRange,
        _retryTrigger
    ) { restaurant, range, _ ->
        restaurant to range
    }.flatMapLatest { (restaurant, range) ->
        if (restaurant == null) {
            flowOf(DetailReportScreenState.SetupRequired)
        } else {
            val period = periodCalculator.calculatePeriods(range).current
            detailedReportsRepository.observePurchaseDetails(restaurant.id, period)
                .map { report ->
                    DetailReportScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        report = report,
                        selectedRange = range,
                        loadedRange = range
                    ) as DetailReportScreenState<PurchaseDetailReport>
                }
                .onStart {
                    val current = uiState.value
                    if (current is DetailReportScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(selectedRange = range, isRefreshing = true, refreshError = false))
                    } else {
                        emit(DetailReportScreenState.Loading)
                    }
                }
                .catch { cause ->
                    val current = uiState.value
                    if (current is DetailReportScreenState.Ready) {
                        emit(current.copy(selectedRange = range, isRefreshing = false, refreshError = true))
                    } else {
                        emit(DetailReportScreenState.Error(cause))
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailReportScreenState.Loading
    )

    fun onRangeSelected(range: DashboardDateRange) {
        _selectedRange.value = range
    }

    fun onRetry() {
        _retryTrigger.value++
    }
}
