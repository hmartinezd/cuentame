package com.venkoi.cuentame.core.database.model

data class InventoryValuationRow(
    val ingredientId: String,
    val ingredientName: String,
    val baseUnitSymbol: String,
    val quantityBase: String,
    val averageUnitCostBase: String?,
    val areaId: String
)

data class PurchaseSpendRow(
    val receiptId: String,
    val purchaseDate: Long,
    val postedAt: Long?,
    val supplierName: String?,
    val lineTotal: String
)

data class WasteValueRow(
    val wasteEventId: String,
    val ingredientId: String,
    val ingredientName: String,
    val areaName: String,
    val reason: String,
    val timestamp: Long,
    val quantityBase: String,
    val baseUnitSymbol: String,
    val totalValueSnapshot: String?,
    val notes: String?
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

/** One immutable posted purchase-line fact used by vendor price intelligence. */
data class VendorPriceObservationRow(
    val purchaseReceiptId: String,
    val purchaseLineId: String,
    val restaurantId: String,
    val currencyCode: String,
    val ingredientId: String,
    val ingredientName: String,
    val baseUnitSymbol: String,
    val supplierId: String?,
    val supplierName: String?,
    val purchaseDate: Long,
    val postedAt: Long?,
    val ingredientUnitOptionId: String,
    val purchaseUnitLabel: String?,
    val quantityEntered: String,
    val quantityBase: String,
    val lineTotal: String,
    val unitCostBase: String,
    val areaId: String,
    val originSnapshotJson: String?,
    val parsedLineEvidenceJson: String?,
    val parsedLineCorrectionJson: String?
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
