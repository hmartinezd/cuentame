package com.venkoi.restaurantops.feature.reports

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.model.dashboard.DashboardDateRange
import com.venkoi.restaurantops.core.presentation.dashboard.DashboardMetricUiModel
import com.venkoi.restaurantops.core.presentation.dashboard.MetricComparisonState
import com.venkoi.restaurantops.feature.reports.ui.*
import com.venkoi.restaurantops.feature.reports.viewmodel.ReportsScreenState
import org.junit.Test
import java.math.BigDecimal

class ReportsScreenStateTest {

    @Test
    fun readyStateCanBeCopied() {
        val report = ReportsUiModel(
            inventory = ReportsInventoryUiModel(BigDecimal.ZERO, 0, 0, null, 0),
            purchases = DashboardMetricUiModel(BigDecimal.ZERO, null, null, null, MetricComparisonState.NO_CHANGE),
            waste = DashboardMetricUiModel(BigDecimal.ZERO, null, null, null, MetricComparisonState.NO_CHANGE),
            alerts = ReportsAlertsUiModel(0, 0, 0),
            counts = ReportsCountUiModel(0, 0, null),
            topWasteItems = emptyList()
        )
        
        val state = ReportsScreenState.Ready(
            restaurantId = RestaurantId("r1"),
            restaurantName = "Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = DashboardDateRange.LAST_30_DAYS,
            loadedRange = DashboardDateRange.LAST_30_DAYS,
            report = report
        )

        val updated = state.copy(isRefreshing = true)
        assertThat(updated.isRefreshing).isTrue()
        assertThat(updated.selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
    }
}
