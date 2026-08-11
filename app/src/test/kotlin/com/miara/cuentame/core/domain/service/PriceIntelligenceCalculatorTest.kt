package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PriceIntelligenceCalculatorTest {
    private val now = Instant.parse("2026-08-11T00:00:00Z")
    private val calculator = PriceIntelligenceCalculator(clock = Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `calculates increase without early rounding and alerts at ten percent boundary`() {
        val comparison = calculator.comparison(listOf(observation("new", "11"), observation("old", "10", daysAgo = 1)))
        assertThat(comparison.absoluteChange!!.compareTo(BigDecimal("1"))).isEqualTo(0)
        assertThat(comparison.percentChange!!.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(comparison.direction).isEqualTo(PriceDirection.INCREASED)
        assertThat(calculator.alert(comparison)).isNotNull()
    }

    @Test fun `below threshold decrease and unchanged do not alert`() {
        val below = calculator.comparison(listOf(observation("new", "10.999"), observation("old", "10", daysAgo = 1)))
        assertThat(calculator.alert(below)).isNull()
        assertThat(calculator.comparison(listOf(observation("n", "9"), observation("o", "10", 1))).direction).isEqualTo(PriceDirection.DECREASED)
        assertThat(calculator.comparison(listOf(observation("n", "10"), observation("o", "10", 1))).direction).isEqualTo(PriceDirection.UNCHANGED)
    }

    @Test fun `zero and missing previous are typed undefined states`() {
        val zero = calculator.comparison(listOf(observation("new", "2"), observation("old", "0", 1)))
        assertThat(zero.percentChange).isNull()
        assertThat(zero.coverage).contains(PriceDataCoverage.PREVIOUS_PRICE_ZERO)
        assertThat(calculator.comparison(listOf(observation("only", "2"))).coverage).contains(PriceDataCoverage.PREVIOUS_PRICE_MISSING)
    }

    @Test fun `latest supplier normalized costs honor inclusive ninety day window`() {
        val rows = listOf(
            observation("a-new", "2.1234567890123456789", 90, supplier = "a"),
            observation("a-old", "1", 89, supplier = "a"),
            observation("b", "2.2", 2, supplier = "b"),
            observation("expired", ".5", 91, supplier = "c")
        )
        val prices = calculator.supplierPrices(rows)
        assertThat(prices).hasSize(2)
        assertThat(prices.single { it.observation.supplierId == SupplierId("a") }.observation.purchaseLineId).isEqualTo(PurchaseLineId("a-new"))
        assertThat(prices.single { it.isLowest }.observation.unitCostBase.compareTo(BigDecimal("2.1234567890123456789"))).isEqualTo(0)
    }

    @Test fun `trustworthy contradictory vendor items are not directly compared`() {
        val comparison = calculator.comparison(listOf(
            observation("new", "12", vendor = "NEW"), observation("old", "10", 1, vendor = "OLD")
        ))
        assertThat(comparison.previous).isNull()
        assertThat(comparison.percentChange).isNull()
        assertThat(comparison.coverage).contains(PriceDataCoverage.CONTRADICTORY_VENDOR_ITEMS)
    }

    private fun observation(id: String, cost: String, daysAgo: Long = 0, supplier: String = "supplier", vendor: String? = null) =
        VendorPriceObservation(PurchaseReceiptId("r-$id"), PurchaseLineId(id), IngredientId("ingredient"), "Flour", "USD", "lb",
            SupplierId(supplier), supplier, now.minusSeconds(daysAgo * 86400), now.minusSeconds(daysAgo * 86400),
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal(cost), BigDecimal(cost), BigDecimal(cost), BigDecimal.ONE,
            "Case", vendor, if (vendor == null) setOf(PriceDataCoverage.VENDOR_ITEM_UNKNOWN) else emptySet())
}
