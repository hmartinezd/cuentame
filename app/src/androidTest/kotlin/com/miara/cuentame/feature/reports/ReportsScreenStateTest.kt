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
                onRetry = { retryClicked = true }
            )
        }
        composeTestRule.onNodeWithTag("reports_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_retry_button").performClick()
        assertThat(retryClicked).isTrue()
    }

    @Test
    fun reportsScreen_ready_displaysAllSections() {
        val readyState = ReportsScreenState.Ready(
            restaurantName = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
            report = ReportsUiModel(
                inventory = ReportsInventoryUiModel(BigDecimal("100"), 1, 1, BigDecimal("100"), 0),
                purchases = DashboardMetricUiModel(BigDecimal("50"), BigDecimal("40"), BigDecimal("10"), BigDecimal("25"), MetricComparisonState.INCREASE),
                waste = DashboardMetricUiModel(BigDecimal("5"), BigDecimal("0"), BigDecimal("5"), null, MetricComparisonState.NEW),
                alerts = ReportsAlertsUiModel(0, 0, 0),
                counts = ReportsCountUiModel(1, 1, null),
                topWasteItems = emptyList()
            )
        )

        composeTestRule.setContent {
            ReportsScreen(
                uiState = readyState,
                onRangeSelected = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithTag("reports_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_header").assertIsDisplayed()
        
        val scrollable = composeTestRule.onNode(hasScrollAction())
        
        scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
        composeTestRule.onNodeWithTag("reports_inventory_section").assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_purchase_section"))
        composeTestRule.onNodeWithTag("reports_purchase_section").assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_waste_section"))
        composeTestRule.onNodeWithTag("reports_waste_section").assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_alerts_section"))
        composeTestRule.onNodeWithTag("reports_alerts_section").assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_stock_count_section"))
        composeTestRule.onNodeWithTag("reports_stock_count_section").assertIsDisplayed()
        
        scrollable.performScrollToNode(hasTestTag("reports_top_waste_list"))
        composeTestRule.onNodeWithTag("reports_top_waste_list").assertIsDisplayed()
    }
}
