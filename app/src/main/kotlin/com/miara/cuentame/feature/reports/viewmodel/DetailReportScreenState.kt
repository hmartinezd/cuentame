package com.miara.cuentame.feature.reports.viewmodel

sealed interface DetailReportScreenState<out T> {
    data object Loading : DetailReportScreenState<Nothing>
    data object SetupRequired : DetailReportScreenState<Nothing>

    data class Ready<T>(
        val restaurantName: String,
        val currencyCode: String,
        val localeTag: String,
        val report: T
    ) : DetailReportScreenState<T>

    data class Error(
        val cause: Throwable
    ) : DetailReportScreenState<Nothing>
}
