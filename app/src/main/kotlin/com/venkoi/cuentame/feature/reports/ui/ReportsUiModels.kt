package com.venkoi.cuentame.feature.reports.ui

import com.venkoi.cuentame.core.model.dashboard.WasteReportItem
import com.venkoi.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import java.math.BigDecimal
import java.time.Instant

data class ReportsInventoryUiModel(
    val totalValue: BigDecimal,
    val valuedIngredientCount: Int,
    val stockedIngredientCount: Int,
    val costCoverage: BigDecimal?, // 0.0 to 100.0
    val missingCostCount: Int
)

data class ReportsAlertsUiModel(
    val negativeBalanceCount: Int,
    val missingCostCount: Int,
    val missingOptionsCount: Int
)

data class ReportsCountUiModel(
    val completedCountCount: Int,
    val adjustedLineCount: Int,
    val mostRecentCompletedCountAt: Instant?
)

data class ReportsUiModel(
    val inventory: ReportsInventoryUiModel,
    val purchases: DashboardMetricUiModel,
    val waste: DashboardMetricUiModel,
    val alerts: ReportsAlertsUiModel,
    val counts: ReportsCountUiModel,
    val topWasteItems: List<WasteReportItem>
)
