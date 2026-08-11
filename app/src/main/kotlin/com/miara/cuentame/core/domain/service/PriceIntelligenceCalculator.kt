package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.domain.repository.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

const val DEFAULT_LARGE_PRICE_INCREASE_PERCENT = 10
const val RECENT_PRICE_WINDOW_DAYS = 90L

class PriceIntelligenceCalculator(
    val alertThresholdPercent: BigDecimal = BigDecimal(DEFAULT_LARGE_PRICE_INCREASE_PERCENT),
    val recentWindowDays: Long = RECENT_PRICE_WINDOW_DAYS,
    private val clock: Clock = Clock.systemUTC()
) {
    fun comparison(observations: List<VendorPriceObservation>): VendorPriceComparison {
        val latest = observations.firstOrNull()
        if (latest == null) return VendorPriceComparison(null, null, null, null, PriceDirection.UNDEFINED, setOf(PriceDataCoverage.PREVIOUS_PRICE_MISSING))
        val remaining = observations.drop(1)
        val previous = remaining.firstOrNull { comparable(latest, it) }
        if (previous == null) {
            val contradiction = remaining.any { candidate ->
                valid(candidate) && candidate.ingredientId == latest.ingredientId && candidate.supplierId == latest.supplierId &&
                    latest.vendorItemCode != null && candidate.vendorItemCode != null && latest.vendorItemCode != candidate.vendorItemCode
            }
            val reason = if (contradiction) PriceDataCoverage.CONTRADICTORY_VENDOR_ITEMS else PriceDataCoverage.PREVIOUS_PRICE_MISSING
            return VendorPriceComparison(latest, null, null, null, PriceDirection.UNDEFINED, latest.coverage + reason)
        }
        val change = latest.unitCostBase.subtract(previous.unitCostBase)
        if (previous.unitCostBase.compareTo(BigDecimal.ZERO) == 0) {
            return VendorPriceComparison(latest, previous, change, null, PriceDirection.UNDEFINED, latest.coverage + previous.coverage + PriceDataCoverage.PREVIOUS_PRICE_ZERO)
        }
        val percent = change.divide(previous.unitCostBase, MathContext.DECIMAL128).multiply(BigDecimal(100))
        val direction = when (change.signum()) { 1 -> PriceDirection.INCREASED; -1 -> PriceDirection.DECREASED; else -> PriceDirection.UNCHANGED }
        return VendorPriceComparison(latest, previous, change, percent, direction, latest.coverage + previous.coverage)
    }

    fun supplierPrices(observations: List<VendorPriceObservation>, now: Instant = clock.instant()): List<SupplierRecentPrice> {
        val cutoff = now.minus(Duration.ofDays(recentWindowDays))
        val latest = observations.filter { it.purchaseDate >= cutoff && valid(it) && it.supplierId != null }
            .groupBy { it.supplierId }.values.map { it.first() }
        val lowest = latest.minOfOrNull { it.unitCostBase }
        return latest.sortedWith(compareBy<VendorPriceObservation> { it.unitCostBase }.thenBy { it.supplierName ?: "" })
            .map { SupplierRecentPrice(it, lowest != null && it.unitCostBase.compareTo(lowest) == 0) }
    }

    fun alert(comparison: VendorPriceComparison): PriceIncreaseAlert? {
        val latest = comparison.latest ?: return null
        val previous = comparison.previous ?: return null
        val percent = comparison.percentChange ?: return null
        if (comparison.direction != PriceDirection.INCREASED || percent < alertThresholdPercent ||
            PriceDataCoverage.CONTRADICTORY_VENDOR_ITEMS in comparison.coverage ||
            PriceDataCoverage.INVALID_HISTORICAL_QUANTITY in comparison.coverage) return null
        return PriceIncreaseAlert(latest.ingredientId, latest.ingredientName, latest.currencyCode, latest.supplierName,
            latest.unitCostBase, previous.unitCostBase, comparison.absoluteChange!!, percent,
            latest.purchaseDate, latest.purchaseReceiptId, latest.purchaseLineId, comparison.coverage)
    }

    private fun valid(o: VendorPriceObservation) = o.quantityEntered > BigDecimal.ZERO && o.quantityBase > BigDecimal.ZERO && o.unitCostBase >= BigDecimal.ZERO
    private fun comparable(a: VendorPriceObservation, b: VendorPriceObservation): Boolean {
        if (!valid(a) || !valid(b) || a.ingredientId != b.ingredientId || a.supplierId != b.supplierId) return false
        return a.vendorItemCode == null || b.vendorItemCode == null || a.vendorItemCode == b.vendorItemCode
    }
}
