package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.feature.reports.ui.*
import com.miara.cuentame.feature.reports.viewmodel.ReportsScreenState
import com.miara.cuentame.feature.home.DashboardMetricUiModel
import com.miara.cuentame.feature.home.MetricComparisonState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class ReportsScreenStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reportsScreen_loading_displaysLoadingIndicator() {
        composeTestRule.setContent {
            ReportsScreen(
                uiState = ReportsScreenState.Loading,
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = {}
            )
        }
        composeTestRule.onNodeWithTag("reports_loading").assertIsDisplayed()
    }

    @Test
    fun reportsScreen_setupRequired_displaysSetupMessage() {
        composeTestRule.setContent {
            ReportsScreen(
                uiState = ReportsScreenState.SetupRequired,
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = {}
            )
        }
        composeTestRule.onNodeWithTag("reports_setup_required").assertIsDisplayed()
    }

    @Test
    fun reportsScreen_error_displaysErrorAndRetryButton() {
        var retryClicked = false
        composeTestRule.setContent {
            ReportsScreen(
                uiState = ReportsScreenState.Error(
                    selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
                    cause = RuntimeException("Test error")
                ),
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = { retryClicked = true }
            )
        }
        composeTestRule.onNodeWithTag("reports_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_retry_button").performClick()
        assertThat(retryClicked).isTrue()
    }

    @Test
    fun reportsScreen_ready_displaysAllSections_withValues() {
        val readyState = ReportsScreenState.Ready(
            restaurantName = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
            report = ReportsUiModel(
                inventory = ReportsInventoryUiModel(BigDecimal("1234.56"), 8, 10, BigDecimal("80.0"), 2),
                purchases = DashboardMetricUiModel(BigDecimal("500"), BigDecimal("400"), BigDecimal("100"), BigDecimal("25"), MetricComparisonState.INCREASE),
                waste = DashboardMetricUiModel(BigDecimal("50"), BigDecimal("20"), BigDecimal("30"), BigDecimal("150"), MetricComparisonState.INCREASE),
                alerts = ReportsAlertsUiModel(1, 2, 3),
                counts = ReportsCountUiModel(4, 5, Instant.EPOCH),
                topWasteItems = listOf(
                    com.miara.cuentame.core.model.dashboard.WasteReportItem(
                        ingredientId = com.miara.cuentame.core.common.ids.IngredientId("ing-1"),
                        name = "Chicken",
                        quantityBase = BigDecimal("5.0"),
                        unitSymbol = "lb",
                        totalValue = BigDecimal("50.00"),
                        eventCount = 2
                    )
                )
            )
        )

        composeTestRule.setContent {
            ReportsScreen(
                uiState = readyState,
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = {}
            )
        }

        val scrollable = composeTestRule.onNode(hasScrollAction())

        // Header Range
        composeTestRule.onNodeWithTag("reports_header", useUnmergedTree = true).onChildren()
            .filter(hasText("30 days", substring = true)).onFirst().assertIsDisplayed()

        // Inventory
        scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
        composeTestRule.onNodeWithTag("reports_inventory_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_inventory_value", useUnmergedTree = true).onChildren()
            .filter(hasText("$1,234.56", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_inventory_coverage", useUnmergedTree = true).onChildren()
            .filter(hasText("8 / 10", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_inventory_coverage", useUnmergedTree = true).onChildren()
            .filter(hasText("80.0%", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_inventory_missing_costs", useUnmergedTree = true).onChildren()
            .filter(hasText("2")).onFirst().assertExists()
        
        // Purchase
        scrollable.performScrollToNode(hasTestTag("reports_purchase_section"))
        composeTestRule.onNodeWithTag("reports_purchase_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
            .filter(hasText("$500.00", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_purchase_section_previous", useUnmergedTree = true).onChildren()
            .filter(hasText("$400.00", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_purchase_section_absolute", useUnmergedTree = true).onChildren()
            .filter(hasText("$100.00", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_purchase_section_trend", useUnmergedTree = true).onChildren()
            .filter(hasText("Increase", substring = true)).onFirst().assertExists()

        // Waste
        scrollable.performScrollToNode(hasTestTag("reports_waste_section"))
        composeTestRule.onNodeWithTag("reports_waste_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_waste_section_current", useUnmergedTree = true).onChildren()
            .filter(hasText("$50.00", substring = true)).onFirst().assertExists()

        // Alerts
        scrollable.performScrollToNode(hasTestTag("reports_alerts_section"))
        composeTestRule.onNodeWithTag("reports_alerts_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_negative_balances", useUnmergedTree = true).onChildren()
            .filter(hasText("1")).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_missing_costs", useUnmergedTree = true).onChildren()
            .filter(hasText("2")).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_missing_unit_options", useUnmergedTree = true).onChildren()
            .filter(hasText("3")).onFirst().assertExists()

        // Counts
        scrollable.performScrollToNode(hasTestTag("reports_stock_count_section"))
        composeTestRule.onNodeWithTag("reports_stock_count_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_completed_counts", useUnmergedTree = true).onChildren()
            .filter(hasText("4")).onFirst().assertExists()
        composeTestRule.onNodeWithTag("reports_adjusted_lines", useUnmergedTree = true).onChildren()
            .filter(hasText("5")).onFirst().assertExists()
        
        // Top Waste
        scrollable.performScrollToNode(hasTestTag("reports_top_waste_list"))
        composeTestRule.onNodeWithTag("reports_top_waste_list", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_top_waste_ing-1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Chicken", useUnmergedTree = true).assertExists()
    }

    @Test
    fun reportsScreen_ready_empty_displaysZeroStates() {
        val readyState = ReportsScreenState.Ready(
            restaurantName = "Empty Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
            report = ReportsUiModel(
                inventory = ReportsInventoryUiModel(BigDecimal.ZERO, 0, 0, null, 0),
                purchases = DashboardMetricUiModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, MetricComparisonState.NO_CHANGE),
                waste = DashboardMetricUiModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, MetricComparisonState.NO_CHANGE),
                alerts = ReportsAlertsUiModel(0, 0, 0),
                counts = ReportsCountUiModel(0, 0, null),
                topWasteItems = emptyList()
            )
        )

        composeTestRule.setContent {
            ReportsScreen(
                uiState = readyState,
                onRangeSelected = {},
                onNavigateToInventory = {},
                onNavigateToPurchases = {},
                onNavigateToWaste = {},
                onRetry = {}
            )
        }

        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
        composeTestRule.onNodeWithTag("reports_inventory_coverage", useUnmergedTree = true).onChildren()
            .filter(hasText("0 / 0", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithText("N/A", substring = true, useUnmergedTree = true).assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_top_waste_list"))
        composeTestRule.onNodeWithTag("reports_top_waste_empty").assertIsDisplayed()
    }
}
