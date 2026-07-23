package com.miara.cuentame.core.common.text

import java.math.BigDecimal

object DecimalParser {
    private val DECIMAL_PATTERN = Regex("^[0-9]+([.,][0-9]+)?$")

    /**
     * Parses a string into a BigDecimal.
     * Supports both dot and comma as decimal separator.
     * Rejects scientific notation, NaN, Infinity, etc.
     */
    fun parse(value: String): BigDecimal? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        
        if (!DECIMAL_PATTERN.matches(trimmed)) {
            return null
        }

        val normalized = trimmed.replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
