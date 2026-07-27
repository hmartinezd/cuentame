package com.miara.cuentame.core.designsystem.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Formatters {
    fun formatCurrency(
        amount: BigDecimal,
        currencyCode: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        try {
            formatter.currency = Currency.getInstance(currencyCode)
        } catch (e: Exception) {
            // Fallback: use generic symbol if currency code is invalid
        }
        // Rounding is usually handled by the currency instance, but we enforce 2 places for safety
        // unless the currency requires more/less (handled by NumberFormat).
        return formatter.format(amount)
    }

    fun formatQuantity(
        quantity: BigDecimal,
        unitSymbol: String? = null
    ): String {
        // Round to 3 decimal places then strip trailing zeros
        val value = quantity.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (unitSymbol != null) "$value $unitSymbol" else value
    }

    fun formatPercent(
        value: Double,
        locale: Locale = Locale.getDefault()
    ): String {
        val formatter = NumberFormat.getPercentInstance(locale)
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        return formatter.format(value)
    }

    fun formatPercent(
        value: BigDecimal,
        locale: Locale = Locale.getDefault()
    ): String {
        val formatter = NumberFormat.getPercentInstance(locale)
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        // MetricComparison percentageChange is e.g. 50.0 for 50%.
        // NumberFormat.getPercentInstance expects 0.5 for 50%.
        return formatter.format(value.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))
    }
}
