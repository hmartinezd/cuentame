package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import org.junit.Test
import java.math.BigDecimal

class StandardUnitConverterTest {
    private val converter = StandardUnitConverter()

    private val grams = UnitOfMeasure(UnitId("g"), "Gram", "g", UnitDimension.MASS, BigDecimal.ONE, true, 1)
    private val kg = UnitOfMeasure(UnitId("kg"), "Kilogram", "kg", UnitDimension.MASS, BigDecimal("1000"), true, 2)
    private val lb = UnitOfMeasure(UnitId("lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal("453.59237"), true, 3)
    private val liters = UnitOfMeasure(UnitId("l"), "Liter", "l", UnitDimension.VOLUME, BigDecimal("1000"), true, 4)

    @Test
    fun `convert same units returns same value`() {
        val result = converter.convert(BigDecimal("10"), grams, grams)
        assertThat(result.compareTo(BigDecimal("10"))).isEqualTo(0)
    }

    @Test
    fun `convert kg to grams`() {
        val result = converter.convert(BigDecimal("2.5"), kg, grams)
        assertThat(result.compareTo(BigDecimal("2500"))).isEqualTo(0)
    }

    @Test
    fun `convert lb to grams`() {
        val result = converter.convert(BigDecimal("1"), lb, grams)
        assertThat(result.compareTo(BigDecimal("453.59237"))).isEqualTo(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `convert incompatible dimensions throws`() {
        converter.convert(BigDecimal.ONE, grams, liters)
    }
}
