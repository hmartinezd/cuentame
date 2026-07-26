package com.miara.cuentame.core.database.model

data class InventoryValuationRow(
    val ingredientId: String,
    val quantityBase: String,
    val averageUnitCostBase: String?
)

data class PurchaseSpendRow(
    val receiptId: String,
    val purchaseDate: Long,
    val lineTotal: String
)

data class WasteValueRow(
    val wasteEventId: String,
    val ingredientId: String,
    val effectiveAt: Long,
    val quantityBaseSigned: String,
    val totalValueSnapshot: String?
)

data class CompletedCountLineRow(
    val stockCountId: String,
    val ingredientId: String,
    val adjustmentQuantityBase: String?
)

data class CompletedCountSummaryRow(
    val stockCountId: String,
    val completedAt: Long
)

data class TopWasteRow(
    val ingredientId: String,
    val ingredientName: String,
    val baseUnitSymbol: String,
    val totalQuantityBase: String,
    val totalWasteValue: String,
    val eventCount: Int
)

data class RecentPurchaseActivityRow(
    val id: String,
    val status: String,
    val postedAt: Long,
    val supplierName: String?,
    val lineTotal: String
)

data class RecentWasteActivityRow(
    val id: String,
    val status: String,
    val timestamp: Long,
    val ingredientName: String,
    val totalValue: String
)

data class RecentCountActivityRow(
    val id: String,
    val status: String,
    val completedAt: Long,
    val name: String
)
