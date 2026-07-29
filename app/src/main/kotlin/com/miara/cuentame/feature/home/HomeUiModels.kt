package com.miara.cuentame.feature.home

import com.miara.cuentame.core.model.dashboard.DashboardActivityItem
import com.miara.cuentame.core.model.dashboard.WasteReportItem
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import java.math.BigDecimal

data class DashboardUiModel(
    val inventoryValue: BigDecimal,
    val valuedIngredientCount: Int,
    val stockedIngredientCount: Int,
    val costCoverage: BigDecimal?, // null if stockedIngredientCount == 0
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
