package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class WeightedAverageCostCalculatorTest {
    private val calculator = WeightedAverageCostCalculator()

    @Test
    fun `calculate first purchase`() {
        val result = calculator.calculate(
            currentQuantity = BigDecimal.ZERO,
            currentAverageCost = BigDecimal.ZERO,
            purchaseQuantity = BigDecimal("10"),
            purchaseUnitCost = BigDecimal("5.5")
        )
        assertThat(result.compareTo(BigDecimal("5.5"))).isEqualTo(0)
    }

    @Test
    fun `calculate with existing inventory`() {
        // (10 * 5) + (10 * 7) = 100 / 20 = 5
        val result = calculator.calculate(
            currentQuantity = BigDecimal("10"),
            currentAverageCost = BigDecimal("5"),
            purchaseQuantity = BigDecimal("10"),
            purchaseUnitCost = BigDecimal("7")
        )
        assertThat(result.compareTo(BigDecimal("6"))).isEqualTo(0)
    }

    @Test
    fun `calculate with zero cost purchase`() {
        val result = calculator.calculate(
            currentQuantity = BigDecimal("10"),
            currentAverageCost = BigDecimal("5"),
            purchaseQuantity = BigDecimal("10"),
            purchaseUnitCost = BigDecimal.ZERO
        )
        assertThat(result.compareTo(BigDecimal("2.5"))).isEqualTo(0)
    }
}
