package com.miara.cuentame.core.designsystem.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormattersTest {

    @Test
    fun formatCurrency_US_Locale() {
        val amount = BigDecimal("1234.56")
        val result = Formatters.formatCurrency(amount, "USD", Locale.US)
        // Note: NBSP might be present in some formatters, using contains for robustness
        assertThat(result).contains("$")
        assertThat(result).contains("1,234.56")
    }

    @Test
    fun formatCurrency_ES_Locale() {
        val amount = BigDecimal("1234.56")
        // Spanish locale typically uses suffix and dot for thousands
        val result = Formatters.formatCurrency(amount, "EUR", Locale("es", "ES"))
        assertThat(result).contains("1.234,56")
        assertThat(result).contains("€")
    }

    @Test
    fun formatPercent_US_Locale() {
        val value = BigDecimal("50.5") // 50.5%
        val result = Formatters.formatPercent(value, Locale.US)
        assertThat(result).isEqualTo("50.5%")
    }

    @Test
    fun formatPercent_ES_Locale() {
        val value = BigDecimal("50.5")
        val result = Formatters.formatPercent(value, Locale("es", "ES"))
        // Spanish uses comma for decimal and space before % sometimes
        assertThat(result).contains("50,5")
        assertThat(result).contains("%")
    }

    @Test
    fun formatQuantity_stripsZeros() {
        val qty = BigDecimal("10.500")
        val result = Formatters.formatQuantity(qty, "lb")
        assertThat(result).isEqualTo("10.5 lb")
    }

    @Test
    fun formatQuantity_roundsToThreePlaces() {
        val qty = BigDecimal("10.12345")
        val result = Formatters.formatQuantity(qty)
        assertThat(result).isEqualTo("10.123")
    }
}
