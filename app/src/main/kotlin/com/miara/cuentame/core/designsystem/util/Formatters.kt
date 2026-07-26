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
            // Fallback if currency code is invalid
        }
        return formatter.format(amount.setScale(2, RoundingMode.HALF_UP))
    }

    fun formatQuantity(
        quantity: BigDecimal,
        unitSymbol: String? = null
    ): String {
        // Round to 3 decimal places then strip zeros
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
        // NumberFormat.getPercentInstance expects 0.1 for 10%, so we divide by 100 if we have the percentage value
        // But MetricComparison percentageChange is e.g. 50.0 for 50%.
        return formatter.format(value.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))
    }
}
