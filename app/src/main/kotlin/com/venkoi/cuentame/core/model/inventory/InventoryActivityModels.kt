package com.venkoi.cuentame.core.model.inventory

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.InventoryMovementId
import com.venkoi.cuentame.core.common.ids.ProductionBatchId
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.common.ids.WasteEventId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class InventoryActivityItem(
    val movement: InventoryMovement,
    val ingredientName: String,
    val areaName: String,
    val baseUnitSymbol: String,
    val sourceInfo: InventoryActivitySourceInfo,
    val reversedByMovementId: InventoryMovementId?,
    val reversalOfDisplay: InventoryActivityRelatedMovementDisplay?,
    val reversedByDisplay: InventoryActivityRelatedMovementDisplay?
)

sealed interface InventoryActivitySourceInfo {

    data class Purchase(
        val supplierName: String?,
        val invoiceNumber: String?,
        val isResolved: Boolean
    ) : InventoryActivitySourceInfo

    data class Waste(
        val reason: WasteReason?,
        val sourceAreaName: String?,
        val isResolved: Boolean
    ) : InventoryActivitySourceInfo

    data class StockCount(
        val countName: String?,
        val isResolved: Boolean
    ) : InventoryActivitySourceInfo

    data class Production(
        val recipeName: String?,
        val status: DocumentStatus?,
        val isResolved: Boolean
    ) : InventoryActivitySourceInfo

    data class Other(
        val sourceDocumentType: SourceDocumentType
    ) : InventoryActivitySourceInfo
}

fun InventoryMovementType.toInventoryActivityCategory(): InventoryActivityCategory = when (this) {
    InventoryMovementType.PURCHASE -> InventoryActivityCategory.PURCHASE
    InventoryMovementType.WASTE -> InventoryActivityCategory.WASTE
    InventoryMovementType.COUNT_ADJUSTMENT -> InventoryActivityCategory.STOCK_COUNT
    InventoryMovementType.PRODUCTION_CONSUMPTION -> InventoryActivityCategory.PRODUCTION_CONSUMPTION
    InventoryMovementType.PRODUCTION_OUTPUT -> InventoryActivityCategory.PRODUCTION_OUTPUT
    InventoryMovementType.SALES_CONSUMPTION -> InventoryActivityCategory.SALES_CONSUMPTION
    InventoryMovementType.REVERSAL -> InventoryActivityCategory.REVERSAL
    InventoryMovementType.MANUAL_ADJUSTMENT -> InventoryActivityCategory.OTHER
    InventoryMovementType.OPENING_BALANCE -> InventoryActivityCategory.OTHER
    InventoryMovementType.UNKNOWN -> InventoryActivityCategory.UNKNOWN
}

fun InventoryMovementType.toDirection(quantity: BigDecimal): InventoryActivityDirection? = when (this) {
    InventoryMovementType.UNKNOWN -> null
    else -> when {
        quantity > BigDecimal.ZERO -> InventoryActivityDirection.IN
        quantity < BigDecimal.ZERO -> InventoryActivityDirection.OUT
        else -> null
    }
}

data class InventoryActivityRelatedMovementDisplay(
    val movementId: InventoryMovementId,
    val category: InventoryActivityCategory,
    val effectiveAt: Instant
)

sealed interface InventoryActivitySourceTarget {
    data class Purchase(val receiptId: PurchaseReceiptId) : InventoryActivitySourceTarget
    data class Waste(val wasteEventId: WasteEventId) : InventoryActivitySourceTarget
    data class StockCount(val stockCountId: StockCountId) : InventoryActivitySourceTarget
    data class Production(val batchId: ProductionBatchId) : InventoryActivitySourceTarget
    data object Unavailable : InventoryActivitySourceTarget
}

enum class InventoryActivityCategory {
    PURCHASE,
    WASTE,
    STOCK_COUNT,
    PRODUCTION_CONSUMPTION,
    PRODUCTION_OUTPUT,
    SALES_CONSUMPTION,
    REVERSAL,
    OTHER,
    UNKNOWN
}

enum class InventoryActivityDirection {
    ALL,
    IN,
    OUT
}

sealed interface InventoryActivityDateRange {
    data object Last7Days : InventoryActivityDateRange
    data object Last30Days : InventoryActivityDateRange
    data object Last90Days : InventoryActivityDateRange
    data class Custom(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate
    ) : InventoryActivityDateRange
}

data class InventoryActivityFilters(
    val dateRange: InventoryActivityDateRange = InventoryActivityDateRange.Last30Days,
    val ingredientId: IngredientId? = null,
    val areaId: InventoryAreaId? = null,
    val categories: Set<InventoryActivityCategory> = InventoryActivityCategory.entries.toSet(),
    val direction: InventoryActivityDirection = InventoryActivityDirection.ALL,
    val includeReversals: Boolean = true
)

data class InventoryActivityQuery(
    val restaurantId: RestaurantId,
    val startInclusive: Instant,
    val endExclusive: Instant,
    val ingredientId: IngredientId? = null,
    val areaId: InventoryAreaId? = null
)

enum class InventoryActivityValueCoverage {
    NONE,
    COMPLETE,
    PARTIAL,
    UNAVAILABLE
}

data class InventoryActivitySummary(
    val movementCount: Int,
    val incomingMovementCount: Int,
    val outgoingMovementCount: Int,
    val reversalCount: Int,
    val valueAdded: BigDecimal,
    val valueRemoved: BigDecimal,
    val valueCoverage: InventoryActivityValueCoverage,
    val quantityCoverage: InventoryActivityValueCoverage,
    val quantitySummary: InventoryActivityQuantitySummary?
)

data class InventoryActivityQuantitySummary(
    val ingredientName: String,
    val baseUnitSymbol: String,
    val quantityIn: BigDecimal,
    val quantityOut: BigDecimal,
    val netQuantity: BigDecimal
)
