package com.venkoi.restaurantops.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CsvWriterTest {

    @Test
    fun `escape should handle plain value`() {
        assertEquals("Plain", CsvWriter.escape("Plain"))
    }

    @Test
    fun `escape should handle value with comma`() {
        assertEquals("\"Value, with comma\"", CsvWriter.escape("Value, with comma"))
    }

    @Test
    fun `escape should handle value with quote`() {
        assertEquals("\"Value with \"\"quote\"\"\"", CsvWriter.escape("Value with \"quote\""))
    }

    @Test
    fun `escape should handle value with newline`() {
        assertEquals("\"Value with\nnewline\"", CsvWriter.escape("Value with\nnewline"))
    }

    @Test
    fun `formatNumber should preserve precision`() {
        assertEquals("1234.56", CsvWriter.formatNumber(BigDecimal("1234.56")))
        assertEquals("100", CsvWriter.formatNumber(BigDecimal("100.000")))
    }

    @Test
    fun `formatNumber should handle null`() {
        assertEquals("", CsvWriter.formatNumber(null))
    }

    @Test
    fun `writeRow should combine values`() {
        val row = CsvWriter.writeRow(listOf("A", "B,C", "D\"E"))
        assertEquals("A,\"B,C\",\"D\"\"E\"", row)
    }
}
