package com.miara.cuentame.feature.reports

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.feature.home.DashboardMetricUiModel
import com.miara.cuentame.feature.home.DashboardUiModel
import com.miara.cuentame.feature.home.HomeScreen
import com.miara.cuentame.feature.home.HomeScreenState
import com.miara.cuentame.feature.home.MetricComparisonState
import com.miara.cuentame.feature.reports.ui.ReportsScreen
import com.miara.cuentame.feature.reports.ui.ReportsUiModel
import com.miara.cuentame.feature.reports.ui.ReportsInventoryUiModel
import com.miara.cuentame.feature.reports.ui.ReportsCountUiModel
import com.miara.cuentame.feature.reports.ui.ReportsAlertsUiModel
import com.miara.cuentame.feature.reports.viewmodel.ReportsScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ReportingRefreshComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_refreshSequence_preservesContentAndScroll() {
        val state = mutableStateOf<HomeScreenState>(
            HomeScreenState.Ready(
                restaurantId = RestaurantId("rest-1"),
                restaurantName = "Test Rest",
                currencyCode = "USD",
                localeTag = "en-US",
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                loadedRange = DashboardDateRange.LAST_30_DAYS,
                dashboard = createPopulatedDashboard("300.00")
            )
        )

        composeTestRule.setContent {
            HomeScreen(
                uiState = state.value,
                onRangeSelected = {},
                onRetry = {},
                onLogWaste = {},
                onViewWaste = {},
                onNewPurchase = {},
                onStartCount = {},
                onViewReports = {}
            )
        }

        // 1. Initial 30-day state
        composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertTextContains("$300.00", substring = true)
        
        // 2. Transition to Refreshing (selected=7, loaded=30)
        state.value = (state.value as HomeScreenState.Ready).copy(
            selectedRange = DashboardDateRange.LAST_7_DAYS,
            isRefreshing = true
        )
        composeTestRule.waitForIdle()

        // 3. Verify flicker-free: refreshing indicator exists
        composeTestRule.onNodeWithTag("home_refreshing").assertExists()
        composeTestRule.onNodeWithTag("home_loading").assertDoesNotExist()
        
        // 4. Verify scroll preservation: scroll to bottom
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("dashboard_recent_activity_list"))
        composeTestRule.onNodeWithTag("dashboard_recent_activity_list").assertIsDisplayed()

        // 5. Complete refresh while at bottom
        state.value = (state.value as HomeScreenState.Ready).copy(
            loadedRange = DashboardDateRange.LAST_7_DAYS,
            dashboard = createPopulatedDashboard("70.00"),
            isRefreshing = false
        )
        composeTestRule.waitForIdle()

        // 6. Verify scroll preserved (bottom item still visible) and content updated
        composeTestRule.onNodeWithTag("dashboard_recent_activity_list").assertIsDisplayed()
        
        // Scroll back to see updated value
        scrollable.performScrollToNode(hasTestTag("dashboard_purchase_spend"))
        composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertTextContains("$70.00", substring = true)
        composeTestRule.onNodeWithTag("home_refreshing").assertDoesNotExist()
    }

    @Test
    fun reportsScreen_refreshError_showsBannerAndPreservesOldData() {
        val state = mutableStateOf<ReportsScreenState>(
            ReportsScreenState.Ready(
                restaurantId = RestaurantId("rest-1"),
                restaurantName = "Test Rest",
                currencyCode = "USD",
                localeTag = "en-US",
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                loadedRange = DashboardDateRange.LAST_30_DAYS,
                report = createPopulatedReport("30.00")
            )
        )

        composeTestRule.setContent {
            ReportsScreen(
                uiState = state.value,
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = {}
            )
        }

        // 1. Initial state
        composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).assert(hasText("$30.00", substring = true) or hasContentDescription("$30.00", substring = true))

        // 2. Transition to Error
        state.value = (state.value as ReportsScreenState.Ready).copy(
            selectedRange = DashboardDateRange.LAST_7_DAYS,
            isRefreshing = false,
            refreshError = true
        )
        composeTestRule.waitForIdle()

        // 3. Verify old data + banner
        composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).assert(hasText("$30.00", substring = true) or hasContentDescription("$30.00", substring = true))
        composeTestRule.onNodeWithTag("reports_refresh_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_error").assertDoesNotExist() // Full screen error should NOT be shown
        
        // Range context should explain the situation
        composeTestRule.onNodeWithTag("reports_range_context").assertTextContains("7 days", substring = true)
        composeTestRule.onNodeWithTag("reports_range_context").assertTextContains("30 days", substring = true)
    }

    private fun createPopulatedDashboard(spend: String) = DashboardUiModel(
        inventoryValue = BigDecimal.ZERO,
        valuedIngredientCount = 0,
        stockedIngredientCount = 0,
        costCoverage = null,
        missingCostCount = 0,
        missingOptionsCount = 0,
        purchaseSpend = DashboardMetricUiModel(BigDecimal(spend), BigDecimal.ZERO, BigDecimal(spend), null, MetricComparisonState.NEW),
        wasteValue = DashboardMetricUiModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, MetricComparisonState.NO_CHANGE),
        negativeBalanceCount = 0,
        completedCountCount = 0,
        mostRecentCompletedCountAt = null,
        adjustedLineCount = 0,
        topWasteItems = emptyList(),
        recentActivity = emptyList()
    )

    private fun createPopulatedReport(spend: String) = ReportsUiModel(
        inventory = ReportsInventoryUiModel(BigDecimal.ZERO, 0, 0, null, 0),
        purchases = DashboardMetricUiModel(BigDecimal(spend), BigDecimal.ZERO, BigDecimal(spend), null, MetricComparisonState.NEW),
        waste = DashboardMetricUiModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, MetricComparisonState.NO_CHANGE),
        alerts = ReportsAlertsUiModel(0, 0, 0),
        counts = ReportsCountUiModel(0, 0, null),
        topWasteItems = emptyList()
    )
}
