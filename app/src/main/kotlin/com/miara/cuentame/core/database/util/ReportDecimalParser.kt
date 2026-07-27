package com.miara.cuentame.core.database.util

import com.miara.cuentame.core.domain.validation.ValidationError
import java.math.BigDecimal

object ReportDecimalParser {

    /**
     * Parses a quantity or cost string. 
     * Throws [ValidationError.InvalidDecimal] if malformed or negative.
     * Note: Inventory allows null cost for stocked ingredients, but this helper
     * handles the decimal parsing part.
     */
    fun parseRequiredNonNegative(value: String?): BigDecimal {
        if (value == null) throw ValidationError.InvalidDecimal
        val decimal = try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.InvalidDecimal
        }
        if (decimal < BigDecimal.ZERO) throw ValidationError.InvalidDecimal
        return decimal
    }

    /**
     * Parses a movement quantity (which can be negative).
     * Throws [ValidationError.InvalidDecimal] if malformed.
     */
    fun parseAny(value: String?): BigDecimal {
        if (value == null) throw ValidationError.InvalidDecimal
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.InvalidDecimal
        }
    }

    /**
     * Specifically for Waste historical snapshots.
     * Throws [ValidationError.MalformedInventoryMovementHistory] if null or malformed.
     */
    fun parseHistoricalSnapshot(value: String?): BigDecimal {
        if (value == null) throw ValidationError.MalformedInventoryMovementHistory
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
    }
}
