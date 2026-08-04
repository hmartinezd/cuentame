package com.miara.cuentame.feature.activity.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.feature.activity.logic.AndroidInventoryActivityTextResolver
import com.miara.cuentame.feature.activity.logic.LocalInventoryActivityTextResolver
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailScreenState
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class InventoryActivityComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = AndroidInventoryActivityTextResolver(context)

    @Test
    fun summary_displaysAllMetrics() {
        val summary = InventoryActivitySummary(
            movementCount = 10,
            incomingMovementCount = 6,
            outgoingMovementCount = 4,
            reversalCount = 1,
            valueAdded = BigDecimal("100.00"),
            valueRemoved = BigDecimal("50.00"),
            valueCoverage = InventoryActivityValueCoverage.COMPLETE,
            quantitySummary = null
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalInventoryActivityTextResolver provides resolver) {
                InventoryActivityListScreen(
                    uiState = InventoryActivityListScreenState.Ready(
                        items = emptyList(),
                        summary = summary,
                        filters = InventoryActivityFilters(),
                        availableIngredients = emptyList(),
                        availableAreas = emptyList(),
                        currencyCode = "USD",
                        localeTag = "en-US",
                        activeFilterCount = 0,
                        today = LocalDate.of(2026, 8, 4)
                    ),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onFilterChange = {},
                    onBackClick = {},
                    onActivityClick = {},
                    onRetry = {},
                    onResetFilters = {}
                )
            }
        }

        val expectedLocale = Locale.forLanguageTag("en-US")
        composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextContains("10")
        composeTestRule.onNodeWithTag("inventory_activity_incoming_count").assertTextContains("6")
        composeTestRule.onNodeWithTag("inventory_activity_outgoing_count").assertTextContains("4")
        composeTestRule.onNodeWithTag("inventory_activity_reversal_count").assertTextContains("1")
        composeTestRule.onNodeWithTag("inventory_activity_value_added").assertTextContains(Formatters.formatCurrency(BigDecimal("100.00"), "USD", expectedLocale))
        composeTestRule.onNodeWithTag("inventory_activity_value_removed").assertTextContains(Formatters.formatCurrency(BigDecimal("50.00"), "USD", expectedLocale))
    }

    @Test
    fun activityRow_displaysItemDetails() {
        val item = InventoryActivityItem(
            movement = InventoryMovement(
                id = InventoryMovementId("m1"),
                restaurantId = RestaurantId("r1"),
                ingredientId = com.miara.cuentame.core.common.ids.IngredientId("ing1"),
                areaId = InventoryAreaId("a1"),
                movementType = InventoryMovementType.PURCHASE,
                quantityBaseSigned = BigDecimal("10.0"),
                unitCostBaseSnapshot = null,
                totalValueSnapshot = BigDecimal("20.0"),
                effectiveAt = Instant.EPOCH,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
                sourceDocumentId = "p1",
                sourceOperationId = "op1",
                sourceLineId = null,
                reversalOfMovementId = null,
                createdAt = Instant.EPOCH
            ),
            ingredientName = "Tomato",
            areaName = "Kitchen",
            baseUnitSymbol = "kg",
            sourceInfo = InventoryActivitySourceInfo.Purchase("US Foods", null, true),
            reversedByMovementId = null,
            reversalOfDisplay = null,
            reversedByDisplay = null
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalInventoryActivityTextResolver provides resolver) {
                InventoryActivityListScreen(
                    uiState = InventoryActivityListScreenState.Ready(
                        items = listOf(item),
                        summary = mockSummary(),
                        filters = InventoryActivityFilters(),
                        availableIngredients = emptyList(),
                        availableAreas = emptyList(),
                        currencyCode = "USD",
                        localeTag = "en-US",
                        activeFilterCount = 0,
                        today = LocalDate.of(2026, 8, 4)
                    ),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onFilterChange = {},
                    onBackClick = {},
                    onActivityClick = {},
                    onRetry = {},
                    onResetFilters = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("inventory_activity_row_m1").assertExists()
        composeTestRule.onNodeWithText("Tomato").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kitchen", substring = true).assertIsDisplayed()
        
        val expectedTitle = resolver.sourceTitle(item.sourceInfo)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText("+10 kg").assertIsDisplayed()
    }

    @Test
    fun detail_showsUnavailableForMissingValues() {
        val item = createActivityItem("m1").copy(
            movement = createActivityItem("m1").movement.copy(
                unitCostBaseSnapshot = null,
                totalValueSnapshot = null
            )
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalInventoryActivityTextResolver provides resolver) {
                InventoryActivityDetailScreen(
                    uiState = InventoryActivityDetailScreenState.Ready(
                        item = item,
                        sourceTarget = InventoryActivitySourceTarget.Unavailable,
                        currencyCode = "USD",
                        localeTag = "en-US"
                    ),
                    onBackClick = {},
                    onOpenSource = {},
                    onOpenMovement = {},
                    onRetry = {}
                )
            }
        }

        // Check unit cost row
        composeTestRule.onNodeWithText("Unit cost").assertExists()
        composeTestRule.onNodeWithText("Not available").assertIsDisplayed()

        // Check total value row
        composeTestRule.onNodeWithText("Total value").assertExists()
        // It appears twice if it matches multiple labels, but here they are unique labels
        composeTestRule.onAllNodesWithText("Not available").assertCountEquals(2)
        
        // Source document button should not be present for Unavailable target
        composeTestRule.onNodeWithTag("inventory_activity_open_source").assertDoesNotExist()
        composeTestRule.onNodeWithText("Source unavailable").assertIsDisplayed()
    }

    private fun createActivityItem(id: String) = InventoryActivityItem(
        movement = InventoryMovement(
            id = InventoryMovementId(id),
            restaurantId = RestaurantId("r1"),
            ingredientId = com.miara.cuentame.core.common.ids.IngredientId("ing1"),
            areaId = InventoryAreaId("a1"),
            movementType = InventoryMovementType.PURCHASE,
            quantityBaseSigned = BigDecimal("10.0"),
            unitCostBaseSnapshot = BigDecimal("2.0"),
            totalValueSnapshot = BigDecimal("20.0"),
            effectiveAt = Instant.EPOCH,
            sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
            sourceDocumentId = "p1",
            sourceOperationId = "op1",
            sourceLineId = null,
            reversalOfMovementId = null,
            createdAt = Instant.EPOCH
        ),
        ingredientName = "Tomato",
        areaName = "Kitchen",
        baseUnitSymbol = "kg",
        sourceInfo = InventoryActivitySourceInfo.Purchase("US Foods", null, false),
        reversedByMovementId = null,
        reversalOfDisplay = null,
        reversedByDisplay = null
    )

    private fun mockSummary() = InventoryActivitySummary(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, InventoryActivityValueCoverage.NONE, null)

    private fun formatSignedQuantity(quantity: BigDecimal, unitSymbol: String): String {
        val prefix = if (quantity > BigDecimal.ZERO) "+" else if (quantity < BigDecimal.ZERO) "\u2212" else ""
        return "$prefix${Formatters.formatQuantity(quantity.abs(), unitSymbol)}"
    }
}
