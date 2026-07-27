package com.miara.cuentame.feature.reports

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.feature.home.DashboardMetricUiModel
import com.miara.cuentame.feature.home.DashboardUiModel
import com.miara.cuentame.feature.home.HomeScreen
import com.miara.cuentame.feature.home.HomeScreenState
import com.miara.cuentame.feature.home.MetricComparisonState
import com.miara.cuentame.feature.reports.ui.*
import com.miara.cuentame.feature.reports.viewmodel.ReportsScreenState
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ReportingRefreshComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_refreshSequence_preservesScroll() {
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
        
        // 2. Transition to Refreshing (keep at top to see indicator)
        state.value = (state.value as HomeScreenState.Ready).copy(
            selectedRange = DashboardDateRange.LAST_7_DAYS,
            isRefreshing = true
        )
        composeTestRule.waitForIdle()

        // 3. Verify flicker-free: content remains, refreshing indicator visible
        composeTestRule.onNodeWithTag("home_refreshing").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_loading").assertDoesNotExist()

        // 4. Verify scroll preservation: scroll to bottom item
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

        // 6. Verify scroll still preserved
        composeTestRule.onNodeWithTag("dashboard_recent_activity_list").assertIsDisplayed()
        
        // Scroll back up to see updated value
        scrollable.performScrollToNode(hasTestTag("dashboard_purchase_spend"))
        composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertTextContains("$70.00", substring = true)
        composeTestRule.onNodeWithTag("home_refreshing").assertDoesNotExist()
    }

    @Test
    fun reportsScreen_refreshError_showsBannerAndContext() {
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

        // Transition to Error
        state.value = (state.value as ReportsScreenState.Ready).copy(
            selectedRange = DashboardDateRange.LAST_7_DAYS,
            refreshError = true
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reports_refresh_error").assertIsDisplayed()
        // Old data still visible
        composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true)
            .assert(hasText("$30.00", substring = true) or hasContentDescription("$30.00", substring = true))
    }

    @Test
    fun wasteDetailScreen_refreshSequence_preservesScroll() {
        val rows = (1..20).map { 
            WasteDetailItem(
                wasteEventId = WasteEventId("w$it"),
                ingredientId = IngredientId("i$it"),
                ingredientName = "Chicken $it",
                areaName = "Area $it",
                reason = WasteReason.SPOILED,
                timestamp = java.time.Instant.now(),
                quantityBase = BigDecimal.ONE,
                baseUnitSymbol = "lb",
                historicalValue = BigDecimal.TEN,
                notes = null
            )
        }
        val state = mutableStateOf<DetailReportScreenState<WasteDetailReport>>(
            DetailReportScreenState.Ready(
                restaurantId = RestaurantId("rest-1"),
                restaurantName = "Test Rest",
                currencyCode = "USD",
                localeTag = "en-US",
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                loadedRange = DashboardDateRange.LAST_30_DAYS,
                report = WasteDetailReport(rows, ReportingPeriod(java.time.Instant.EPOCH, java.time.Instant.now()), BigDecimal("30.00"), rows.size)
            )
        )

        composeTestRule.setContent {
            WasteDetailScreen(
                uiState = state.value,
                onRangeSelected = {},
                onRetry = {}
            )
        }

        // Scroll to middle item
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("waste_report_item_w15"))
        composeTestRule.onNodeWithTag("waste_report_item_w15").assertExists()

        // Transition to refreshing
        state.value = (state.value as DetailReportScreenState.Ready).copy(
            selectedRange = DashboardDateRange.LAST_7_DAYS,
            isRefreshing = true
        )
        composeTestRule.waitForIdle()

        // Scroll back to top to see indicator
        scrollable.performScrollToNode(hasTestTag("waste_report_range_selector"))
        composeTestRule.onNodeWithTag("waste_report_refreshing").assertIsDisplayed()
        
        // Scroll back to middle
        scrollable.performScrollToNode(hasTestTag("waste_report_item_w15"))
        composeTestRule.onNodeWithTag("waste_report_item_w15").assertExists()

        // Complete refresh
        state.value = (state.value as DetailReportScreenState.Ready).copy(
            loadedRange = DashboardDateRange.LAST_7_DAYS,
            report = WasteDetailReport(rows.take(5), ReportingPeriod(java.time.Instant.EPOCH, java.time.Instant.now()), BigDecimal("7.00"), 5),
            isRefreshing = false
        )
        composeTestRule.waitForIdle()

        // Row w15 should be gone, but list should not have jumped destructively (it will just be at bottom if items decreased significantly)
        composeTestRule.onNodeWithTag("waste_report_item_w15").assertDoesNotExist()
        composeTestRule.onNodeWithTag("waste_report_total").assertTextContains("$7.00", substring = true)
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
        recentActivity = (1..10).map { 
            DashboardActivityItem(it.toString(), DashboardActivityType.PURCHASE, "POSTED", java.time.Instant.now(), "Supplier $it", BigDecimal.TEN)
        }
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
