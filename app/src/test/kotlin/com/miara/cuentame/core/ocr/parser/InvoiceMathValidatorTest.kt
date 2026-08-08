package com.miara.cuentame.core.ocr.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InvoiceMathValidatorTest {

    @Test
    fun `line math exact match`() {
        assertTrue(InvoiceMathValidator.isLineMathValid(
            BigDecimal("3"),
            BigDecimal("10.50"),
            BigDecimal("31.50")
        ))
    }

    @Test
    fun `line math within tolerance`() {
        // 3 * 10.50 = 31.50. 31.51 is within 0.02 tolerance
        assertTrue(InvoiceMathValidator.isLineMathValid(
            BigDecimal("3"),
            BigDecimal("10.50"),
            BigDecimal("31.51")
        ))
    }

    @Test
    fun `line math boundary tolerance`() {
        // 31.52 is exactly on the 0.02 boundary
        assertTrue(InvoiceMathValidator.isLineMathValid(
            BigDecimal("3"),
            BigDecimal("10.50"),
            BigDecimal("31.52")
        ))
    }

    @Test
    fun `line math outside tolerance`() {
        assertFalse(InvoiceMathValidator.isLineMathValid(
            BigDecimal("3"),
            BigDecimal("10.50"),
            BigDecimal("31.53")
        ))
    }

    @Test
    fun `invoice equation standard`() {
        // 100 - 5 + 10 + 7.35 = 112.35
        assertTrue(InvoiceMathValidator.isInvoiceMathValid(
            subtotal = BigDecimal("100.00"),
            discount = BigDecimal("5.00"),
            fees = BigDecimal("10.00"),
            tax = BigDecimal("7.35"),
            total = BigDecimal("112.35")
        ))
    }

    @Test
    fun `invoice equation with nulls`() {
        // Minimal valid
        assertTrue(InvoiceMathValidator.isInvoiceMathValid(
            subtotal = BigDecimal("100.00"),
            discount = null,
            fees = null,
            tax = null,
            total = BigDecimal("100.00")
        ))
    }

    @Test
    fun `invoice equation outside tolerance`() {
        assertFalse(InvoiceMathValidator.isInvoiceMathValid(
            subtotal = BigDecimal("100.00"),
            discount = BigDecimal("5.00"),
            fees = BigDecimal("10.00"),
            tax = BigDecimal("7.35"),
            total = BigDecimal("112.38") // 0.03 off
        ))
    }

    @Test
    fun `large money values precision`() {
        // 3 * 41152263.04 = 123456789.12
        assertTrue(InvoiceMathValidator.isLineMathValid(
            quantity = BigDecimal("3"),
            unitPrice = BigDecimal("41152263.04"),
            lineTotal = BigDecimal("123456789.12")
        ))
    }

    @Test
    fun `negative monetary values`() {
        // Credit line: -2 * 10.00 = -20.00
        assertTrue(InvoiceMathValidator.isLineMathValid(
            quantity = BigDecimal("-2"),
            unitPrice = BigDecimal("10.00"),
            lineTotal = BigDecimal("-20.00")
        ))
    }

    @Test
    fun `IEEE-754 regression 3 times 0 point 10`() {
        // 3 * 0.10 = 0.30 (not 0.30000000000000004)
        assertTrue(InvoiceMathValidator.isLineMathValid(
            quantity = BigDecimal("3"),
            unitPrice = BigDecimal("0.10"),
            lineTotal = BigDecimal("0.30")
        ))
    }
}
