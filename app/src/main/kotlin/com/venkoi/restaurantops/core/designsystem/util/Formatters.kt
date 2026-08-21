package com.venkoi.restaurantops.core.designsystem.util

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
        val currency = try {
            Currency.getInstance(currencyCode)
        } catch (e: Exception) {
            null
        }

        return if (currency != null) {
            formatter.currency = currency
            formatter.format(amount)
        } else {
            // Fallback: preserve the requested code and use a generic decimal format
            val decimalFormatter = NumberFormat.getNumberInstance(locale)
            decimalFormatter.minimumFractionDigits = 2
            decimalFormatter.maximumFractionDigits = 2
            "$currencyCode ${decimalFormatter.format(amount)}"
        }
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

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val unit = "KMGTPE"[exp - 1] + "B"
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), unit)
    }
}
