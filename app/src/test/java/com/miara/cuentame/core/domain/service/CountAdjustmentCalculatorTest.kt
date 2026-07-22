package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class CountAdjustmentCalculatorTest {
    private val calculator = CountAdjustmentCalculator()

    @Test
    fun `positive adjustment`() {
        val result = calculator.calculateAdjustment(BigDecimal("10"), BigDecimal("8"))
        assertThat(result.compareTo(BigDecimal("2"))).isEqualTo(0)
    }

    @Test
    fun `negative adjustment`() {
        val result = calculator.calculateAdjustment(BigDecimal("5"), BigDecimal("8"))
        assertThat(result.compareTo(BigDecimal("-3"))).isEqualTo(0)
    }
}
