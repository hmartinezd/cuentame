package com.venkoi.restaurantops.feature.home

import com.venkoi.restaurantops.core.model.dashboard.DashboardActivityItem
import com.venkoi.restaurantops.core.model.dashboard.WasteReportItem
import com.venkoi.restaurantops.core.presentation.dashboard.DashboardMetricUiModel
import java.math.BigDecimal
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import com.venkoi.restaurantops.core.common.ids.IngredientId

data class SetupReadinessUiModel(
    val areas: List<InventoryArea>,
    val ingredientCount: Int,
    val invalidUnitCount: Int,
    val unassignedIngredientIds: List<IngredientId>,
    val hasCompletedInitialCount: Boolean
) {
    val coreReady: Boolean get() = areas.isNotEmpty() && ingredientCount > 0 && invalidUnitCount == 0 && unassignedIngredientIds.isEmpty() && hasCompletedInitialCount
}

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
