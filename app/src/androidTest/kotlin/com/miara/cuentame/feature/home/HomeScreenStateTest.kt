package com.miara.cuentame.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.R
import com.miara.cuentame.core.model.dashboard.DashboardActivityItem
import com.miara.cuentame.core.model.dashboard.DashboardActivityType
import com.miara.cuentame.core.model.dashboard.InventoryValuationSummary
import com.miara.cuentame.core.model.dashboard.MetricComparison
import com.miara.cuentame.core.model.dashboard.WasteReportItem
import com.miara.cuentame.core.common.ids.IngredientId
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HomeScreenStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_loading_displaysLoadingIndicator() {
        composeTestRule.setContent {
            HomeScreen(
                uiState = HomeScreenState.Loading,
                onRangeSelected = {},
                onRetry = {},
                onLogWaste = {},
                onViewWaste = {},
                onNewPurchase = {},
                onStartCount = {},
                onViewReports = {}
            )
        }

        composeTestRule.onNodeWithTag("home_loading").assertIsDisplayed()
    }

    @Test
    fun homeScreen_setupRequired_displaysSetupMessage() {
        composeTestRule.setContent {
            HomeScreen(
                uiState = HomeScreenState.SetupRequired,
                onRangeSelected = {},
                onRetry = {},
                onLogWaste = {},
                onViewWaste = {},
                onNewPurchase = {},
                onStartCount = {},
                onViewReports = {}
            )
        }

        composeTestRule.onNodeWithTag("home_setup_required").assertIsDisplayed()
    }

    @Test
    fun homeScreen_error_displaysErrorAndRetryButton() {
        var retryClicked = false
        composeTestRule.setContent {
            HomeScreen(
                uiState = HomeScreenState.Error(
                    selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
                    cause = RuntimeException("Test error")
                ),
                onRangeSelected = {},
                onRetry = { retryClicked = true },
                onLogWaste = {},
                onViewWaste = {},
                onNewPurchase = {},
                onStartCount = {},
                onViewReports = {}
            )
        }

        composeTestRule.onNodeWithTag("home_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_retry_button").performClick()
        assertThat(retryClicked).isTrue()
    }

    @Test
    fun homeScreen_ready_displaysContentWithMetrics() {
        val readyState = HomeScreenState.Ready(
            restaurantName = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            selectedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
            loadedRange = com.miara.cuentame.core.model.dashboard.DashboardDateRange.LAST_30_DAYS,
            dashboard = com.miara.cuentame.feature.home.DashboardUiModel(
                inventoryValue = BigDecimal("1000.00"),
                valuedIngredientCount = 10,
                stockedIngredientCount = 12,
                costCoverage = BigDecimal("83.33"),
                missingCostCount = 2,
                missingOptionsCount = 0,
                purchaseSpend = com.miara.cuentame.feature.home.DashboardMetricUiModel(
                    value = BigDecimal("500.00"),
                    previousValue = BigDecimal("400.00"),
                    absoluteChange = BigDecimal("100.00"),
                    percentageChange = BigDecimal("25"),
                    comparisonState = MetricComparisonState.INCREASE
                ),
                wasteValue = com.miara.cuentame.feature.home.DashboardMetricUiModel(
                    value = BigDecimal("50.00"),
                    previousValue = BigDecimal("0.00"),
                    absoluteChange = BigDecimal("50.00"),
                    percentageChange = null,
                    comparisonState = MetricComparisonState.NEW
                ),
                negativeBalanceCount = 0,
                completedCountCount = 1,
                mostRecentCompletedCountAt = Instant.now(),
                adjustedLineCount = 3,
                topWasteItems = listOf(
                    WasteReportItem(
                        ingredientId = IngredientId("ing-1"),
                        name = "Chicken",
                        quantityBase = BigDecimal("5.0"),
                        unitSymbol = "lb",
                        totalValue = BigDecimal("50.00"),
                        eventCount = 2
                    )
                ),
                recentActivity = emptyList()
            )
        )

        composeTestRule.setContent {
            HomeScreen(
                uiState = readyState,
                onRangeSelected = {},
                onRetry = {},
                onLogWaste = {},
                onViewWaste = {},
                onNewPurchase = {},
                onStartCount = {},
                onViewReports = {}
            )
        }

        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_inventory_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_waste_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_data_completeness").assertIsDisplayed()
    }
}

