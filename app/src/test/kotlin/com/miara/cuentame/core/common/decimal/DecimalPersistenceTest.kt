package com.miara.cuentame.core.common.decimal

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class DecimalPersistenceTest {
    @Test
    fun `toStorageString preserves plain format`() {
        val decimal = BigDecimal("0.00000001")
        assertThat(decimal.toStorageString()).isEqualTo("0.00000001")
    }

    @Test
    fun `toBigDecimalValue parses canonical string`() {
        val string = "123.456789"
        assertThat(string.toBigDecimalValue().compareTo(BigDecimal("123.456789"))).isEqualTo(0)
    }

    @Test(expected = Exception::class)
    fun `toBigDecimalValue fails on invalid string`() {
        "abc".toBigDecimalValue()
    }
}
