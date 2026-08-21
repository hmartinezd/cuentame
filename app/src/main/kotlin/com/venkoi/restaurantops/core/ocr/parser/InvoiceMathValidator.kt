package com.venkoi.restaurantops.core.ocr.parser

import com.venkoi.restaurantops.core.common.decimal.MoneyComparison
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Centralizes financial validation logic for invoice parsing.
 * Ensures all math is performed using BigDecimal with appropriate rounding and tolerance.
 */
object InvoiceMathValidator {

    /**
     * Validates that quantity * unitPrice equals lineTotal within tolerance.
     */
    fun isLineMathValid(
        quantity: BigDecimal?,
        unitPrice: BigDecimal?,
        lineTotal: BigDecimal?
    ): Boolean {
        if (quantity == null || unitPrice == null || lineTotal == null) return true
        
        val expected = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
        return MoneyComparison.moneyApproximatelyEquals(expected, lineTotal)
    }

    /**
     * Validates the overall invoice equation: subtotal - discount + fees + tax = total.
     * Note: This assumes discount is a positive value to be subtracted. 
     * If discount is already negative, it will be added correctly if we use subtraction: 100 - (-5) = 105 (incorrect).
     * The business rule is that subtotal, tax, fees, and total are usually positive, 
     * and discount reduces the total.
     */
    fun isInvoiceMathValid(
        subtotal: BigDecimal?,
        discount: BigDecimal?,
        fees: BigDecimal?,
        tax: BigDecimal?,
        total: BigDecimal?
    ): Boolean {
        if (subtotal == null || total == null) return true
        
        var calculated = subtotal
        
        // discount is subtracted
        if (discount != null) {
            calculated = calculated.subtract(discount)
        }
        
        // fees are added
        if (fees != null) {
            calculated = calculated.add(fees)
        }
        
        // tax is added
        if (tax != null) {
            calculated = calculated.add(tax)
        }
        
        val expected = calculated.setScale(2, RoundingMode.HALF_UP)
        return MoneyComparison.moneyApproximatelyEquals(expected, total)
    }
}
