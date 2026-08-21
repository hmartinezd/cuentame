package com.venkoi.restaurantops.core.model.dashboard

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.common.ids.WasteEventId
import com.venkoi.restaurantops.core.domain.service.ReportingPeriod
import com.venkoi.restaurantops.core.model.inventory.WasteReason
import java.math.BigDecimal
import java.time.Instant

// --- Inventory Detail ---

data class InventoryDetailItem(
    val ingredientId: IngredientId,
    val ingredientName: String,
    val baseUnitSymbol: String,
    val totalQuantityBase: BigDecimal,
    val currentAverageCost: BigDecimal?,
    val currentInventoryValue: BigDecimal?,
    val stockedAreaCount: Int,
    val negativeAreaBalanceCount: Int,
    val isMissingCost: Boolean
)

data class InventoryDetailReport(
    val rows: List<InventoryDetailItem>,
    val totalValue: BigDecimal,
    val recordCount: Int,
    val valuedIngredientCount: Int,
    val stockedIngredientCount: Int,
    val missingCostCount: Int,
    val negativeBalanceCount: Int
)

// --- Purchase Detail ---

data class PurchaseDetailItem(
    val purchaseId: PurchaseReceiptId,
    val purchaseDate: Instant,
    val postedAt: Instant?,
    val supplierName: String?,
    val lineCount: Int,
    val total: BigDecimal
)

data class PurchaseDetailReport(
    val rows: List<PurchaseDetailItem>,
    val period: ReportingPeriod,
    val totalSpend: BigDecimal,
    val recordCount: Int
)

// --- Waste Detail ---

data class WasteDetailItem(
    val wasteEventId: WasteEventId,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val areaName: String,
    val reason: WasteReason,
    val timestamp: Instant,
    val quantityBase: BigDecimal,
    val baseUnitSymbol: String,
    val historicalValue: BigDecimal,
    val notes: String?
)

data class WasteDetailReport(
    val rows: List<WasteDetailItem>,
    val period: ReportingPeriod,
    val totalWasteValue: BigDecimal,
    val recordCount: Int
)
