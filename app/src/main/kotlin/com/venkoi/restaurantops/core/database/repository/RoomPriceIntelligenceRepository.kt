package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.dao.PurchaseDao
import com.venkoi.restaurantops.core.database.model.VendorPriceObservationRow
import com.venkoi.restaurantops.core.domain.repository.*
import com.venkoi.restaurantops.core.domain.service.PriceIntelligenceCalculator
import com.venkoi.restaurantops.core.ocr.parser.ParsedInvoiceLineCandidate
import com.venkoi.restaurantops.core.ocr.parser.ParsedInvoiceLineCorrection
import com.venkoi.restaurantops.core.ocr.parser.effectiveValue
import com.venkoi.restaurantops.core.ocr.parser.matching.InventoryNormalization
import com.venkoi.restaurantops.core.database.entity.PurchaseLineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomPriceIntelligenceRepository @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val activeRestaurantProvider: ActiveRestaurantProvider,
    private val json: Json
) : PriceIntelligenceRepository {
    private val calculator = PriceIntelligenceCalculator()

    override fun observeIngredientPriceHistory(ingredientId: IngredientId): Flow<IngredientPriceHistory> =
        activeRestaurantProvider.observeActiveRestaurant().flatMapLatest { restaurant ->
            purchaseDao.observeVendorPriceRows(restaurant?.id ?: "", ingredientId.value)
        }.map { rows ->
            val observations = rows.map(::toObservation)
            IngredientPriceHistory(observations, calculator.comparison(observations), calculator.supplierPrices(observations))
        }

    override fun observeLargePriceIncreases(): Flow<List<PriceIncreaseAlert>> =
        activeRestaurantProvider.observeActiveRestaurant().flatMapLatest { restaurant ->
            purchaseDao.observeVendorPriceRows(restaurant?.id ?: "", null)
        }.map { rows ->
            rows.map(::toObservation).groupBy { it.ingredientId to it.supplierId }.values
                .mapNotNull { calculator.alert(calculator.comparison(it)) }
                .sortedWith(compareByDescending<PriceIncreaseAlert> { it.percentIncrease }
                    .thenByDescending { it.purchaseDate }.thenBy { it.ingredientName })
        }

    override fun observePriceComparisons(restaurantId: RestaurantId, ingredientIds: Set<IngredientId>): Flow<Map<IngredientId, VendorPriceComparison>> =
        purchaseDao.observeVendorPriceRows(restaurantId.value, null).map { rows ->
            rows.map(::toObservation).filter { it.ingredientId in ingredientIds }
                .groupBy { it.ingredientId }
                .mapValues { calculator.comparison(it.value) }
        }

    private fun toObservation(row: VendorPriceObservationRow): VendorPriceObservation {
        val entered = row.quantityEntered.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val base = row.quantityBase.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val total = row.lineTotal.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val unitCost = row.unitCostBase.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val validQuantity = entered > BigDecimal.ZERO && base > BigDecimal.ZERO
        val provenanceExists = row.originSnapshotJson != null
        val provenanceTrusted = provenanceExists && row.asEntity().matchesMaterializationSnapshot(row.originSnapshotJson, json)
        val vendorCode = if (provenanceTrusted) vendorCode(row) else null
        val normalizedVendorKey = vendorCode?.let(InventoryNormalization::normalizeVendorCode)?.takeIf(String::isNotBlank)
        val coverage = buildSet {
            if (!validQuantity) add(PriceDataCoverage.INVALID_HISTORICAL_QUANTITY)
            if (historicalPackageLabel(row, provenanceTrusted).isNullOrBlank()) add(PriceDataCoverage.PACKAGE_LABEL_UNKNOWN)
            if (provenanceExists && !provenanceTrusted) add(PriceDataCoverage.SOURCE_PROVENANCE_DIVERGED)
            if (vendorCode == null) add(PriceDataCoverage.VENDOR_ITEM_UNKNOWN)
        }
        return VendorPriceObservation(
            PurchaseReceiptId(row.purchaseReceiptId), PurchaseLineId(row.purchaseLineId),
            IngredientId(row.ingredientId), row.ingredientName, row.currencyCode, row.baseUnitSymbol,
            row.supplierId?.let(::SupplierId), row.supplierName,
            Instant.ofEpochMilli(row.purchaseDate), row.postedAt?.let(Instant::ofEpochMilli),
            entered, base, total,
            entered.takeIf { it > BigDecimal.ZERO }?.let { total.divide(it, MathContext.DECIMAL128) },
            unitCost,
            entered.takeIf { it > BigDecimal.ZERO }?.let { base.divide(it, MathContext.DECIMAL128) },
            historicalPackageLabel(row, provenanceTrusted), vendorCode, coverage, normalizedVendorKey
        )
    }

    private fun VendorPriceObservationRow.asEntity() = PurchaseLineEntity(
        purchaseLineId, purchaseReceiptId, ingredientId, areaId, ingredientUnitOptionId,
        quantityEntered, quantityBase, lineTotal, unitCostBase, null, 0, 0
    )

    /** Decode only the immutable accepted parse source; current supplier mappings are never consulted. */
    private fun vendorCode(row: VendorPriceObservationRow): String? = runCatching {
        val evidence = row.parsedLineEvidenceJson?.let { json.decodeFromString<ParsedInvoiceLineCandidate>(it) }
            ?: return null
        val correction = row.parsedLineCorrectionJson?.takeIf { it != "null" }
            ?.let { json.decodeFromString<ParsedInvoiceLineCorrection>(it) }
        evidence.vendorCode.effectiveValue(correction?.vendorCode)?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    /** Current unit-option labels are mutable; only immutable, trusted invoice package text is historical. */
    private fun historicalPackageLabel(row: VendorPriceObservationRow, provenanceTrusted: Boolean): String? = runCatching {
        if (!provenanceTrusted) return null
        val evidence = row.parsedLineEvidenceJson?.let { json.decodeFromString<ParsedInvoiceLineCandidate>(it) }
            ?: return null
        val correction = row.parsedLineCorrectionJson?.takeIf { it != "null" }
            ?.let { json.decodeFromString<ParsedInvoiceLineCorrection>(it) }
        evidence.packageText.effectiveValue(correction?.packageText)?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()
}
