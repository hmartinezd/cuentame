package com.miara.cuentame.feature.purchases.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.designsystem.util.Formatters
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PurchaseDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    
    private val fixedNow = Instant.parse("2026-08-05T10:00:00Z")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault())

    @Test
    fun unknown_status_displays_unavailable_label_and_no_mutation_controls() {
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

        val unavailableText = context.getString(R.string.status_unavailable)
        val draftText = context.getString(R.string.status_draft)
        val postedText = context.getString(R.string.status_posted)
        val voidedText = context.getString(R.string.status_voided)

        // 1. Verify status label (merged semantics)
        composeTestRule.onNodeWithTag("purchase_status_chip")
            .assertTextContains(unavailableText)
        
        // 2. Verify it does NOT show other statuses
        composeTestRule.onNodeWithTag("purchase_status_chip")
            .assert(hasText(draftText).not())
            .assert(hasText(postedText).not())
            .assert(hasText(voidedText).not())

        // 5. Header information
        composeTestRule.onNodeWithText("Test Supplier").assertIsDisplayed()
        composeTestRule.onNodeWithText(dateFormatter.format(fixedNow)).assertIsDisplayed()
        
        val invoiceLabel = context.getString(R.string.invoice_number)
        composeTestRule.onNodeWithTag("purchase_invoice_number").assertTextEquals("$invoiceLabel: INV-123")

        // 9-15. Purchase lines
        val line = details.lines.first()
        val lineId = line.line.id.value
        
        composeTestRule.onNodeWithTag("purchase_line_ingredient_$lineId", useUnmergedTree = true).assertTextEquals("Chicken")
        
        val entered = Formatters.formatQuantity(line.line.quantityEntered, line.unitOptionName)
        val base = Formatters.formatQuantity(line.line.quantityBase, line.baseUnitSymbol)
        composeTestRule.onNodeWithTag("purchase_line_quantity_$lineId", useUnmergedTree = true).assertTextEquals("$entered ($base)")
        
        val areaLabel = context.getString(R.string.receiving_area)
        composeTestRule.onNodeWithTag("purchase_line_area_$lineId", useUnmergedTree = true).assertTextEquals("$areaLabel: Kitchen")
        
        val expectedTotal = Formatters.formatCurrency(line.line.lineTotal, "USD")
        composeTestRule.onNodeWithTag("purchase_line_total_$lineId", useUnmergedTree = true).assertTextEquals(expectedTotal)
        
        val expectedUnitCost = Formatters.formatCurrency(line.line.unitCostBase, "USD")
        val unitCostLabel = "$expectedUnitCost per ${line.baseUnitSymbol}"
        composeTestRule.onNodeWithTag("purchase_line_unit_cost_$lineId", useUnmergedTree = true)
            .assertTextEquals(unitCostLabel)

        // Receipt total
        val totalAmount = details.lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.line.lineTotal) }
        val expectedReceiptTotal = Formatters.formatCurrency(totalAmount, "USD")
        composeTestRule.onNodeWithTag("purchase_receipt_total").assertTextEquals(expectedReceiptTotal)

        // 16-20. Verify mutation controls are hidden
        composeTestRule.onNodeWithTag("purchase_void_button").assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.void_purchase)).assertDoesNotExist()
        // No Add Line, Edit Line, Delete Line (these are not in the read-only screen)
    }

    @Test
    fun unknown_status_handles_missing_optional_fields_safely() {
        val details = PurchaseDetails(
            receipt = PurchaseReceipt(
                id = PurchaseReceiptId("p1"),
                restaurantId = RestaurantId("r1"),
                supplierId = null,
                invoiceNumber = null,
                purchaseDate = fixedNow,
                status = DocumentStatus.UNKNOWN,
                notes = null,
                attachmentPath = null,
                createdAt = fixedNow,
                updatedAt = fixedNow,
                postedAt = null,
                voidedAt = null
            ),
            supplierName = null,
            lines = listOf(
                PurchaseLineWithDetails(
                    line = PurchaseLine(
                        id = PurchaseLineId("l1"),
                        purchaseReceiptId = PurchaseReceiptId("p1"),
                        ingredientId = IngredientId("i1"),
                        areaId = InventoryAreaId("a1"),
                        ingredientUnitOptionId = IngredientUnitOptionId("o1"),
                        quantityEntered = BigDecimal("10"),
                        quantityBase = BigDecimal("10"),
                        lineTotal = BigDecimal("80"),
                        unitCostBase = BigDecimal("8"),
                        notes = null,
                        createdAt = fixedNow,
                        updatedAt = fixedNow
                    ),
                    ingredientName = null, // Missing
                    areaName = null, // Missing
                    unitOptionName = null, // Missing
                    baseUnitSymbol = null // Missing
                )
            )
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

        val noSupplierText = context.getString(R.string.no_supplier)
        val invoiceLabel = context.getString(R.string.invoice_number)
        val uncategorizedText = context.getString(R.string.uncategorized)
        val areaLabel = context.getString(R.string.receiving_area)

        composeTestRule.onNodeWithText(noSupplierText).assertIsDisplayed()
        composeTestRule.onNodeWithText(invoiceLabel, substring = true).assertDoesNotExist()
        
        val lineId = "l1"
        composeTestRule.onNodeWithTag("purchase_line_ingredient_$lineId", useUnmergedTree = true).assertTextEquals(uncategorizedText)
        
        // Quantity formatting should be safe even with null unit
        val quantityText = "${Formatters.formatQuantity(BigDecimal("10"), null)} (${Formatters.formatQuantity(BigDecimal("10"), null)})"
        composeTestRule.onNodeWithTag("purchase_line_quantity_$lineId", useUnmergedTree = true).assertTextEquals(quantityText)
        
        // Area should show label with empty value
        composeTestRule.onNodeWithTag("purchase_line_area_$lineId", useUnmergedTree = true).assertTextEquals("$areaLabel: ")
        
        // Unit cost and total should remain safe
        val expectedTotal = Formatters.formatCurrency(BigDecimal("80"), "USD")
        composeTestRule.onNodeWithTag("purchase_line_total_$lineId", useUnmergedTree = true).assertTextEquals(expectedTotal)
        
        val expectedReceiptTotal = Formatters.formatCurrency(BigDecimal("80"), "USD")
        composeTestRule.onNodeWithTag("purchase_receipt_total").assertTextEquals(expectedReceiptTotal)
    }

    @Test
    fun unknown_status_displays_historical_timestamps_when_present() {
        val postedAt = fixedNow.minusSeconds(3600)
        val voidedAt = fixedNow.minusSeconds(1800)
        
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

        val postedAtLabel = context.getString(R.string.posted_at, timeFormatter.format(postedAt))
        val voidedAtLabel = context.getString(R.string.voided_at, timeFormatter.format(voidedAt))
        val unavailableText = context.getString(R.string.status_unavailable)

        // It should show both timestamps if present, even if status is UNKNOWN
        composeTestRule.onNodeWithTag("purchase_posted_at").assertTextEquals(postedAtLabel)
        composeTestRule.onNodeWithTag("purchase_voided_at").assertTextEquals(voidedAtLabel)
        
        // But status chip should still say Unavailable
        composeTestRule.onNodeWithTag("purchase_status_chip").assertTextContains(unavailableText)
    }

    @Test
    fun unknown_status_with_no_timestamps_renders_safely() {
        val details = createDetails(status = DocumentStatus.UNKNOWN, postedAt = null, voidedAt = null)
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

        val postedAtPrefix = context.getString(R.string.posted_at, "").substringBefore("%")
        val voidedAtPrefix = context.getString(R.string.voided_at, "").substringBefore("%")

        composeTestRule.onNodeWithText(postedAtPrefix, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(voidedAtPrefix, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
    }

    private fun createDetails(
        status: DocumentStatus,
        postedAt: Instant? = null,
        voidedAt: Instant? = null
    ): PurchaseDetails {
        val receiptId = PurchaseReceiptId("p1")
        val restId = RestaurantId("r1")
        
        return PurchaseDetails(
            receipt = PurchaseReceipt(
                id = receiptId,
                restaurantId = restId,
                supplierId = SupplierId("s1"),
                invoiceNumber = "INV-123",
                purchaseDate = fixedNow,
                status = status,
                notes = "Some notes",
                attachmentPath = null,
                createdAt = fixedNow,
                updatedAt = fixedNow,
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
                        createdAt = fixedNow,
                        updatedAt = fixedNow
                    ),
                    ingredientName = "Chicken",
                    areaName = "Kitchen",
                    unitOptionName = "lb",
                    baseUnitSymbol = "lb"
                ),
                PurchaseLineWithDetails(
                    line = PurchaseLine(
                        id = PurchaseLineId("l2"),
                        purchaseReceiptId = receiptId,
                        ingredientId = IngredientId("i2"),
                        areaId = InventoryAreaId("a2"),
                        ingredientUnitOptionId = IngredientUnitOptionId("o2"),
                        quantityEntered = BigDecimal("5"),
                        quantityBase = BigDecimal("5"),
                        lineTotal = BigDecimal("25"),
                        unitCostBase = BigDecimal("5"),
                        notes = null,
                        createdAt = fixedNow,
                        updatedAt = fixedNow
                    ),
                    ingredientName = "Potatoes",
                    areaName = "Pantry",
                    unitOptionName = "lb",
                    baseUnitSymbol = "lb"
                )
            )
        )
    }
}
