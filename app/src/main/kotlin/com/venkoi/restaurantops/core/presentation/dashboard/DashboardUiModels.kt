package com.venkoi.restaurantops.core.presentation.dashboard

import java.math.BigDecimal

data class DashboardMetricUiModel(
    val value: BigDecimal,
    val previousValue: BigDecimal?,
    val absoluteChange: BigDecimal?,
    val percentageChange: BigDecimal?,
    val comparisonState: MetricComparisonState
)

enum class MetricComparisonState {
    INCREASE,
    DECREASE,
    NO_CHANGE,
    NEW,
    UNAVAILABLE
}
