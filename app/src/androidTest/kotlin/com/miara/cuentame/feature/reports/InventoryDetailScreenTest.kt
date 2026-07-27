package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.dashboard.InventoryDetailItem
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport
import com.miara.cuentame.feature.reports.ui.InventoryDetailScreen
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class InventoryDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun inventoryReport_loading_displaysLoadingIndicator() {
        composeTestRule.setContent {
            InventoryDetailScreen(
                uiState = DetailReportScreenState.Loading,
                onRetry = {}
            )
        }
        composeTestRule.onNodeWithTag("inventory_report_loading").assertIsDisplayed()
    }

    @Test
    fun inventoryReport_setupRequired_displaysSetupMessage() {
        composeTestRule.setContent {
            InventoryDetailScreen(
                uiState = DetailReportScreenState.SetupRequired,
                onRetry = {}
            )
        }
        composeTestRule.onNodeWithTag("inventory_report_setup_required").assertIsDisplayed()
    }

    @Test
    fun inventoryReport_error_displaysErrorAndRetryButton() {
        var retryClicked = false
        composeTestRule.setContent {
            InventoryDetailScreen(
                uiState = DetailReportScreenState.Error(RuntimeException("Test error")),
                onRetry = { retryClicked = true }
            )
        }
        composeTestRule.onNodeWithTag("inventory_report_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inventory_report_error_retry").assertIsDisplayed().performClick()
        assertThat(retryClicked).isTrue()
    }

    @Test
    fun inventoryReport_ready_displaysSummaryAndItems() {
        val readyState = DetailReportScreenState.Ready(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            report = InventoryDetailReport(
                rows = listOf(
                    InventoryDetailItem(
                        ingredientId = IngredientId("ing1"),
                        ingredientName = "Chicken",
                        baseUnitSymbol = "lb",
                        totalQuantityBase = BigDecimal("10.0"),
                        currentAverageCost = BigDecimal("2.50"),
                        currentInventoryValue = BigDecimal("25.00"),
                        stockedAreaCount = 1,
                        negativeAreaBalanceCount = 0,
                        isMissingCost = false
                    ),
                    InventoryDetailItem(
                        ingredientId = IngredientId("ing2"),
                        ingredientName = "Milk",
                        baseUnitSymbol = "gal",
                        totalQuantityBase = BigDecimal("5.0"),
                        currentAverageCost = null,
                        currentInventoryValue = null,
                        stockedAreaCount = 1,
                        negativeAreaBalanceCount = 1,
                        isMissingCost = true
                    )
                ),
                totalValue = BigDecimal("25.00"),
                recordCount = 2,
                valuedIngredientCount = 1,
                stockedIngredientCount = 2,
                missingCostCount = 1,
                negativeBalanceCount = 0 // Aggregate is positive
            )
        )

        composeTestRule.setContent {
            InventoryDetailScreen(
                uiState = readyState,
                onRetry = {}
            )
        }

        // Header
        composeTestRule.onNodeWithText("Test Rest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inventory Detail").assertIsDisplayed()

        // Summary
        composeTestRule.onNodeWithTag("inventory_report_total_value")
            .assertTextContains("$25.00", substring = true)
        
        // Item 1 (Chicken)
        val chickenRow = composeTestRule.onNodeWithTag("inventory_report_item_ing1")
        chickenRow.assertIsDisplayed()
        chickenRow.assertTextContains("Chicken", substring = true)
        chickenRow.assertTextContains("10 lb", substring = true)
        chickenRow.assertTextContains("$25.00", substring = true)

        // Item 2 (Milk) - with warnings
        val milkRow = composeTestRule.onNodeWithTag("inventory_report_item_ing2")
        milkRow.assertIsDisplayed()
        milkRow.assertTextContains("Milk", substring = true)
        milkRow.assertTextContains("N/A", substring = true)
        composeTestRule.onAllNodesWithText("Missing current cost").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("Negative area balances").onFirst().assertExists()
    }

    @Test
    fun inventoryReport_empty_displaysEmptyMessage() {
        val readyState = DetailReportScreenState.Ready(
            restaurantName = "Empty Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            report = InventoryDetailReport(emptyList(), BigDecimal.ZERO, 0, 0, 0, 0, 0)
        )

        composeTestRule.setContent {
            InventoryDetailScreen(
                uiState = readyState,
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithTag("inventory_report_empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("No stocked ingredients").assertIsDisplayed()
    }
}
