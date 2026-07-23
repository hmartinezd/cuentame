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
        val value = quantity.stripTrailingZeros().toPlainString()
        return if (unitSymbol != null) "$value $unitSymbol" else value
    }
}
