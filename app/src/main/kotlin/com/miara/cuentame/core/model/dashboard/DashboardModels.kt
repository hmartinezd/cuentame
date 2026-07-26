package com.miara.cuentame.core.model.dashboard

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.inventory.DocumentStatus
import java.math.BigDecimal
import java.time.Instant

enum class DashboardDateRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS
}

data class MetricComparison(
    val current: BigDecimal,
    val previous: BigDecimal,
    val absoluteChange: BigDecimal,
    val percentageChange: BigDecimal?
)

data class InventoryValuationSummary(
    val totalValue: BigDecimal,
    val valuedIngredientCount: Int,
    val stockedIngredientCount: Int,
    val missingCostCount: Int
)

data class WasteReportItem(
    val ingredientId: IngredientId,
    val name: String,
    val quantityBase: BigDecimal,
    val unitSymbol: String,
    val totalValue: BigDecimal,
    val eventCount: Int
)

enum class DashboardActivityType {
    PURCHASE,
    WASTE,
    STOCK_COUNT
}

data class DashboardActivityItem(
    val id: String,
    val type: DashboardActivityType,
    val status: String, // String representation of domain status
    val timestamp: Instant,
    val description: String,
    val value: BigDecimal? = null
)

data class DashboardSnapshot(
    val inventory: InventoryValuationSummary,
    val purchases: MetricComparison,
    val waste: MetricComparison,
    val negativeBalanceCount: Int,
    val completedCountCount: Int,
    val mostRecentCompletedCountAt: Instant?,
    val adjustedLineCount: Int,
    val activeIngredientsMissingOptionsCount: Int,
    val topWasteItems: List<WasteReportItem>,
    val recentActivity: List<DashboardActivityItem>
)
