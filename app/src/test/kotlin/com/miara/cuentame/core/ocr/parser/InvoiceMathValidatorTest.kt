package com.miara.cuentame.core.ocr.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InvoiceMathValidatorTest {

    @Test
    fun `isInvoiceMathValid handles positive discount by subtraction`() {
        // Subtotal: 100.00
        // Discount: 5.00 (Positive value means "reduce by 5")
        // Fees: 0.00
        // Tax: 7.00
        // Total: 102.00 (100 - 5 + 0 + 7 = 102)
        assertTrue(
            InvoiceMathValidator.isInvoiceMathValid(
                subtotal = BigDecimal("100.00"),
                discount = BigDecimal("5.00"),
                fees = BigDecimal("0.00"),
                tax = BigDecimal("7.00"),
                total = BigDecimal("102.00")
            )
        )
    }

    @Test
    fun `isInvoiceMathValid documentation - negative discount is added by subtract(minus)`() {
        /**
         * CRITICAL MILESTONE 5C CONTRACT DOCUMENTATION:
         * 
         * The current implementation uses: calculated = subtotal.subtract(discount)
         * If discount is -5.00, then: 100.00 - (-5.00) = 105.00
         * 
         * This test proves the behavior so it is not accidentally changed without a migration.
         * In many accounting systems, a negative discount is actually a fee.
         */
        
        // Subtotal: 100.00
        // Discount: -5.00
        // Fees: 0.00
        // Tax: 7.00
        // Total: 100 - (-5) + 0 + 7 = 112.00
        assertTrue(
            InvoiceMathValidator.isInvoiceMathValid(
                subtotal = BigDecimal("100.00"),
                discount = BigDecimal("-5.00"),
                fees = BigDecimal("0.00"),
                tax = BigDecimal("7.00"),
                total = BigDecimal("112.00")
            )
        )
        
        // Conversely, a total of 102.00 fails if discount is -5.00
        assertFalse(
            InvoiceMathValidator.isInvoiceMathValid(
                subtotal = BigDecimal("100.00"),
                discount = BigDecimal("-5.00"),
                fees = BigDecimal("0.00"),
                tax = BigDecimal("7.00"),
                total = BigDecimal("102.00")
            )
        )
    }
}
