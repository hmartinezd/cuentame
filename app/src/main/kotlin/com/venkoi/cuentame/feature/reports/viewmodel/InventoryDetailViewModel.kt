package com.venkoi.cuentame.feature.reports.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.domain.repository.DetailedReportsRepository
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.model.dashboard.InventoryDetailReport
import com.venkoi.cuentame.feature.reports.export.InventoryCsvExport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val restaurantRepository: RestaurantRepository,
    private val detailedReportsRepository: DetailedReportsRepository
) : ViewModel() {

    private val filterValue: String? = savedStateHandle.get<String>("filter")

    private val _retryTrigger = MutableStateFlow(0)

    private val _exportTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportFlow: Flow<String> = _exportTrigger.asSharedFlow()

    private val _exportError = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val exportError: Flow<Throwable> = _exportError.asSharedFlow()

    private var isExporting = false

    val uiState: StateFlow<DetailReportScreenState<InventoryDetailReport>> = combine(
        restaurantRepository.observeRestaurant(),
        _retryTrigger
    ) { restaurant, _ ->
        restaurant
    }.flatMapLatest { restaurant ->
        if (restaurant == null) {
            flowOf(DetailReportScreenState.SetupRequired)
        } else {
            detailedReportsRepository.observeInventoryDetails(restaurant.id)
                .map { report ->
                    val filteredRows = when (filterValue) {
                        "negative" -> report.rows.filter { it.negativeAreaBalanceCount > 0 }
                        "missing_cost" -> report.rows.filter { it.isMissingCost }
                        else -> report.rows
                    }
                    val filteredReport = if (filteredRows.size != report.rows.size) {
                        report.copy(rows = filteredRows)
                    } else {
                        report
                    }

                    DetailReportScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        report = filteredReport
                    ) as DetailReportScreenState<InventoryDetailReport>
                }
                .onStart {
                    val current = uiState.value
                    if (current is DetailReportScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(isRefreshing = true, refreshError = false))
                    } else {
                        emit(DetailReportScreenState.Loading)
                    }
                }
                .catch { cause ->
                    val current = uiState.value
                    if (current is DetailReportScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(isRefreshing = false, refreshError = true))
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

    fun onRetry() {
        _retryTrigger.value++
    }

    fun onExportRequested() {
        val state = uiState.value
        if (state is DetailReportScreenState.Ready && !isExporting) {
            isExporting = true
            viewModelScope.launch {
                try {
                    val csv = InventoryCsvExport.generate(state.report)
                    _exportTrigger.emit(csv)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _exportError.emit(e)
                } finally {
                    isExporting = false
                }
            }
        }
    }
}
