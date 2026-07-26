package com.miara.cuentame.feature.home

import com.miara.cuentame.core.model.dashboard.DashboardActivityItem
import com.miara.cuentame.core.model.dashboard.WasteReportItem
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

data class DashboardUiModel(
    val inventoryValue: BigDecimal,
    val costCoverage: Double?, // null if stockedIngredientCount == 0
    val missingCostCount: Int,
    val missingOptionsCount: Int,
    val purchaseSpend: DashboardMetricUiModel,
    val wasteValue: DashboardMetricUiModel,
    val negativeBalanceCount: Int,
    val completedCountCount: Int,
    val mostRecentCompletedCountAt: java.time.Instant?,
    val adjustedLineCount: Int,
    val topWasteItems: List<WasteReportItem>,
    val recentActivity: List<DashboardActivityItem>
)
