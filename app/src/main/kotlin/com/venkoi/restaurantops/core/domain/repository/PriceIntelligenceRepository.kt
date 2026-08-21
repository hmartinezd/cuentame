package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

enum class PriceDataCoverage {
    COMPLETE, VENDOR_ITEM_UNKNOWN, PACKAGE_LABEL_UNKNOWN,
    INVALID_HISTORICAL_QUANTITY, PREVIOUS_PRICE_MISSING, PREVIOUS_PRICE_ZERO,
    CONTRADICTORY_VENDOR_ITEMS, NO_RECENT_COMPARABLE_SUPPLIER_PRICE,
    SOURCE_PROVENANCE_DIVERGED
}

data class VendorPriceObservation(
    val purchaseReceiptId: PurchaseReceiptId,
    val purchaseLineId: PurchaseLineId,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val currencyCode: String,
    val baseUnitSymbol: String,
    val supplierId: SupplierId?,
    val supplierName: String?,
    val purchaseDate: Instant,
    val postedAt: Instant?,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val lineTotal: BigDecimal,
    val enteredUnitPrice: BigDecimal?,
    val unitCostBase: BigDecimal,
    val historicalConversionRatio: BigDecimal?,
    val purchaseUnitLabel: String?,
    val vendorItemCode: String?,
    val coverage: Set<PriceDataCoverage>,
    val normalizedVendorItemKey: String? = vendorItemCode
)

enum class PriceDirection { INCREASED, DECREASED, UNCHANGED, UNDEFINED }

data class VendorPriceComparison(
    val latest: VendorPriceObservation?,
    val previous: VendorPriceObservation?,
    val absoluteChange: BigDecimal?,
    val percentChange: BigDecimal?,
    val direction: PriceDirection,
    val coverage: Set<PriceDataCoverage>
)

data class SupplierRecentPrice(
    val observation: VendorPriceObservation,
    val isLowest: Boolean
)

data class IngredientPriceHistory(
    val observations: List<VendorPriceObservation>,
    val comparison: VendorPriceComparison,
    val recentSupplierPrices: List<SupplierRecentPrice>
)

data class PriceIncreaseAlert(
    val ingredientId: IngredientId,
    val ingredientName: String,
    val currencyCode: String,
    val supplierName: String?,
    val latestCost: BigDecimal,
    val previousCost: BigDecimal,
    val absoluteIncrease: BigDecimal,
    val percentIncrease: BigDecimal,
    val purchaseDate: Instant,
    val purchaseReceiptId: PurchaseReceiptId,
    val purchaseLineId: PurchaseLineId,
    val coverage: Set<PriceDataCoverage>
)

interface PriceIntelligenceRepository {
    fun observeIngredientPriceHistory(ingredientId: IngredientId): Flow<IngredientPriceHistory>
    fun observePriceComparisons(restaurantId: RestaurantId, ingredientIds: Set<IngredientId>): Flow<Map<IngredientId, VendorPriceComparison>>
    fun observeLargePriceIncreases(): Flow<List<PriceIncreaseAlert>>
}
