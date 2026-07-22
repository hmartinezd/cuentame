package com.miara.cuentame.core.common.text

import java.math.BigDecimal
import java.util.Locale

object DecimalParser {
    /**
     * Parses a string into a BigDecimal.
     * Supports both dot and comma as decimal separator.
     */
    fun parse(value: String): BigDecimal? {
        if (value.isBlank()) return null
        
        val normalized = value.trim().replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
