package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.model.VendorPriceObservationRow
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.service.PriceIntelligenceCalculator
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection
import com.miara.cuentame.core.ocr.parser.effectiveValue
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

    private fun toObservation(row: VendorPriceObservationRow): VendorPriceObservation {
        val entered = row.quantityEntered.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val base = row.quantityBase.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val total = row.lineTotal.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val unitCost = row.unitCostBase.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val validQuantity = entered > BigDecimal.ZERO && base > BigDecimal.ZERO
        val coverage = buildSet {
            if (!validQuantity) add(PriceDataCoverage.INVALID_HISTORICAL_QUANTITY)
            if (row.purchaseUnitLabel.isNullOrBlank()) add(PriceDataCoverage.PACKAGE_LABEL_UNKNOWN)
            if (vendorCode(row) == null) add(PriceDataCoverage.VENDOR_ITEM_UNKNOWN)
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
            row.purchaseUnitLabel, vendorCode(row), coverage
        )
    }

    /** Decode only the immutable accepted parse source; current supplier mappings are never consulted. */
    private fun vendorCode(row: VendorPriceObservationRow): String? = runCatching {
        val evidence = row.parsedLineEvidenceJson?.let { json.decodeFromString<ParsedInvoiceLineCandidate>(it) }
            ?: return null
        val correction = row.parsedLineCorrectionJson?.takeIf { it != "null" }
            ?.let { json.decodeFromString<ParsedInvoiceLineCorrection>(it) }
        evidence.vendorCode.effectiveValue(correction?.vendorCode)?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()
}
