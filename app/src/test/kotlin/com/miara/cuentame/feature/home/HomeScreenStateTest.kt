package com.miara.cuentame.feature.home

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
import org.junit.Test
import java.math.BigDecimal

class HomeScreenStateTest {

    @Test
    fun `Ready state can be copied and updated`() {
        val dashboard = DashboardUiModel(
            inventoryValue = BigDecimal("100.00"),
            valuedIngredientCount = 10,
            stockedIngredientCount = 15,
            costCoverage = BigDecimal("66.7"),
            missingCostCount = 5,
            missingOptionsCount = 2,
            purchaseSpend = DashboardMetricUiModel(BigDecimal.ZERO, null, null, null, MetricComparisonState.NO_CHANGE),
            wasteValue = DashboardMetricUiModel(BigDecimal.ZERO, null, null, null, MetricComparisonState.NO_CHANGE),
            negativeBalanceCount = 0,
            completedCountCount = 3,
            mostRecentCompletedCountAt = null,
            adjustedLineCount = 1,
            topWasteItems = emptyList(),
            recentActivity = emptyList()
        )
        
        val state = HomeScreenState.Ready(
            restaurantId = RestaurantId("r1"),
            restaurantName = "Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = DashboardDateRange.LAST_30_DAYS,
            loadedRange = DashboardDateRange.LAST_30_DAYS,
            dashboard = dashboard
        )

        val refreshingState = state.copy(isRefreshing = true)
        assertThat(refreshingState.isRefreshing).isTrue()
        assertThat(refreshingState.restaurantId).isEqualTo(RestaurantId("r1"))
    }
}
