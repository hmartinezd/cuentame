package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class CountComparisonCalculatorTest {
    private val calculator = CountComparisonCalculator()

    @Test
    fun `calculateUnclassifiedUsage with simple values`() {
        val result = calculator.calculateUnclassifiedUsage(
            initialInventory = BigDecimal("20"),
            purchases = BigDecimal("80"),
            recordedWaste = BigDecimal("3"),
            finalInventory = BigDecimal("65")
        )
        // 20 + 80 - 3 - 65 = 32
        assertThat(result.compareTo(BigDecimal("32"))).isEqualTo(0)
    }
}
