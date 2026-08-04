package com.miara.cuentame.core.model.inventory

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.WasteEventId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class InventoryActivityItem(
    val movement: InventoryMovement,
    val ingredientName: String,
    val areaName: String,
    val baseUnitSymbol: String,
    val sourceDisplay: InventoryActivitySourceDisplay,
    val reversedByMovementId: InventoryMovementId?,
    val reversalOfDisplay: InventoryActivityRelatedMovementDisplay?,
    val reversedByDisplay: InventoryActivityRelatedMovementDisplay?
)

data class InventoryActivitySourceDisplay(
    val title: String,
    val subtitle: String?,
    val status: String?
)

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
    REVERSAL,
    OTHER
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
    val includeReversals: Boolean = true,
    val searchQuery: String = ""
)

data class InventoryActivityQuery(
    val restaurantId: RestaurantId,
    val startInclusive: Instant,
    val endExclusive: Instant,
    val ingredientId: IngredientId? = null,
    val areaId: InventoryAreaId? = null
)

data class InventoryActivitySummary(
    val movementCount: Int,
    val incomingMovementCount: Int,
    val outgoingMovementCount: Int,
    val reversalCount: Int,
    val valueAdded: BigDecimal?,
    val valueRemoved: BigDecimal?,
    val quantitySummary: InventoryActivityQuantitySummary?
)

data class InventoryActivityQuantitySummary(
    val ingredientName: String,
    val baseUnitSymbol: String,
    val quantityIn: BigDecimal,
    val quantityOut: BigDecimal,
    val netQuantity: BigDecimal
)
