package com.miara.cuentame.feature.purchases.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailUiState
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PurchaseDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unknown_status_displays_unavailable_label_and_no_void_button() {
        val details = createDetails(DocumentStatus.UNKNOWN)
        val uiState = PurchaseDetailUiState(
            state = PurchaseDetailState.Ready(details),
            currencyCode = "USD"
        )

        composeTestRule.setContent {
            PurchaseDetailScreen(
                uiState = uiState,
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onVoid = {}
            )
        }

        // Verify status label
        composeTestRule.onNodeWithTag("purchase_status_chip", useUnmergedTree = true)
            .assertTextContains("Unavailable", ignoreCase = true)

        // Verify details are visible
        composeTestRule.onNodeWithText("Test Supplier").assertIsDisplayed()
        composeTestRule.onNodeWithText("INV-123", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Chicken").assertIsDisplayed()
        composeTestRule.onNodeWithText("10 lb", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("80.00", substring = true).assertIsDisplayed()

        // Verify mutation controls are hidden
        composeTestRule.onNodeWithTag("purchase_void_button").assertDoesNotExist()
    }

    @Test
    fun unknown_status_handles_missing_optional_fields() {
        val details = PurchaseDetails(
            receipt = PurchaseReceipt(
                id = PurchaseReceiptId("p1"),
                restaurantId = RestaurantId("r1"),
                supplierId = null,
                invoiceNumber = null,
                purchaseDate = Instant.now(),
                status = DocumentStatus.UNKNOWN,
                notes = null,
                attachmentPath = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                postedAt = null,
                voidedAt = null
            ),
            supplierName = null,
            lines = emptyList()
        )
        val uiState = PurchaseDetailUiState(
            state = PurchaseDetailState.Ready(details),
            currencyCode = "USD"
        )

        composeTestRule.setContent {
            PurchaseDetailScreen(
                uiState = uiState,
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onVoid = {}
            )
        }

        composeTestRule.onNodeWithText("No supplier", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Invoice", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
    }

    @Test
    fun unknown_status_displays_historical_timestamps() {
        val now = Instant.parse("2026-08-05T10:00:00Z")
        val postedAt = now.minusSeconds(3600)
        val voidedAt = now.minusSeconds(1800)
        
        val details = createDetails(
            status = DocumentStatus.UNKNOWN,
            postedAt = postedAt,
            voidedAt = voidedAt
        )
        val uiState = PurchaseDetailUiState(
            state = PurchaseDetailState.Ready(details),
            currencyCode = "USD"
        )

        composeTestRule.setContent {
            PurchaseDetailScreen(
                uiState = uiState,
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onVoid = {}
            )
        }

        // It should show both timestamps if present, even if status is UNKNOWN
        composeTestRule.onNodeWithText("Posted at", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Voided at", substring = true).assertIsDisplayed()
        
        // But status chip should still say Unavailable
        composeTestRule.onNodeWithTag("purchase_status_chip", useUnmergedTree = true)
            .assertTextContains("Unavailable", ignoreCase = true)
    }

    private fun createDetails(
        status: DocumentStatus,
        postedAt: Instant? = null,
        voidedAt: Instant? = null
    ): PurchaseDetails {
        val receiptId = PurchaseReceiptId("p1")
        val restId = RestaurantId("r1")
        val now = Instant.now()
        
        return PurchaseDetails(
            receipt = PurchaseReceipt(
                id = receiptId,
                restaurantId = restId,
                supplierId = SupplierId("s1"),
                invoiceNumber = "INV-123",
                purchaseDate = now,
                status = status,
                notes = "Some notes",
                attachmentPath = null,
                createdAt = now,
                updatedAt = now,
                postedAt = postedAt,
                voidedAt = voidedAt
            ),
            supplierName = "Test Supplier",
            lines = listOf(
                PurchaseLineWithDetails(
                    line = PurchaseLine(
                        id = PurchaseLineId("l1"),
                        purchaseReceiptId = receiptId,
                        ingredientId = IngredientId("i1"),
                        areaId = InventoryAreaId("a1"),
                        ingredientUnitOptionId = IngredientUnitOptionId("o1"),
                        quantityEntered = BigDecimal("10"),
                        quantityBase = BigDecimal("10"),
                        lineTotal = BigDecimal("80"),
                        unitCostBase = BigDecimal("8"),
                        notes = null,
                        createdAt = now,
                        updatedAt = now
                    ),
                    ingredientName = "Chicken",
                    areaName = "Kitchen",
                    unitOptionName = "lb",
                    baseUnitSymbol = "lb"
                )
            )
        )
    }
}
