package com.miara.cuentame.feature.reports.viewmodel

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.model.dashboard.DashboardDateRange

sealed interface DetailReportScreenState<out T> {
    data object Loading : DetailReportScreenState<Nothing>
    data object SetupRequired : DetailReportScreenState<Nothing>

    data class Ready<T>(
        val restaurantId: RestaurantId,
        val restaurantName: String,
        val currencyCode: String,
        val localeTag: String,
        val report: T,
        val isRefreshing: Boolean = false,
        val refreshError: Boolean = false,
        val selectedRange: DashboardDateRange? = null,
        val loadedRange: DashboardDateRange? = null
    ) : DetailReportScreenState<T>

    data class Error(
        val cause: Throwable
    ) : DetailReportScreenState<Nothing>
}
