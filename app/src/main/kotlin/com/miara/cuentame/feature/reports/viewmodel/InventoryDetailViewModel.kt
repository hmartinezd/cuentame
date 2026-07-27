package com.miara.cuentame.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val detailedReportsRepository: DetailedReportsRepository
) : ViewModel() {

    private val _retryTrigger = MutableStateFlow(0)

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
                    DetailReportScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        report = report
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
}
