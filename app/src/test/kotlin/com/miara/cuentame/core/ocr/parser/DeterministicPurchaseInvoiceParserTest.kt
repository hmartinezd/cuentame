package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DeterministicPurchaseInvoiceParserTest {

    private val parser = DeterministicPurchaseInvoiceParser()

    @Test
    fun `headerless standard invoice extraction`() {
        val page = createPage(
            listOf(
                "000101          TOMATO ROMA           25 LB         2      31.50      63.00",
                "000102          ONION YELLOW          50 LB         1      28.00      28.00",
                "000103          CHICKEN BREAST        40 LB         3      54.25     162.75"
            )
        )

        val result = parser.parse(listOf(page))

        assertTrue("Should have InferredColumnLayout warning", result.warnings.contains(InvoiceParseWarning.InferredColumnLayout))
        assertEquals(3, result.lines.size)

        val line1 = result.lines[0]
        assertEquals("000101", line1.vendorCode.normalizedValue)
        assertTrue("Description should contain TOMATO ROMA", line1.description.normalizedValue?.contains("TOMATO ROMA") == true)
        assertEquals("25 LB", line1.packageText.normalizedValue)
        assertEquals(BigDecimal("2"), line1.quantity.normalizedValue)
        assertEquals(BigDecimal("31.50"), line1.unitPrice.normalizedValue)
        assertEquals(BigDecimal("63.00"), line1.lineTotal.normalizedValue)
    }

    @Test
    fun `distinct-row support requirement`() {
        val pageSingle = createPage(
            listOf(
                "FOOTER INFO   10.00   20.00   30.00"
            )
        )
        val resultSingle = parser.parse(listOf(pageSingle))
        assertTrue("Should NOT infer layout with only 1 supporting row", 
            resultSingle.warnings.contains(InvoiceParseWarning.UnknownColumnLayout))

        val pageMulti = createPage(
            listOf(
                "ITEM A        10.00    10.00",
                "ITEM B        20.00    20.00",
                "ITEM C        30.00    30.00"
            )
        )
        val resultMulti = parser.parse(listOf(pageMulti))
        assertFalse("Should infer layout with 3 supporting rows", 
            resultMulti.warnings.contains(InvoiceParseWarning.UnknownColumnLayout))
    }

    @Test
    fun `invoice full equation validation`() {
        val page = createPage(
            listOf(
                "Subtotal       100.00",
                "Discount       5.00",
                "Delivery Fee   10.00",
                "Tax            7.35",
                "Total          112.35"
            )
        )
        val result = parser.parse(listOf(page))
        assertFalse("Should not have InvoiceMathMismatch", result.warnings.contains(InvoiceParseWarning.InvoiceMathMismatch))
        assertEquals(BigDecimal("100.00"), result.subtotal.normalizedValue)
        assertEquals(BigDecimal("112.35"), result.total.normalizedValue)
    }

    @Test
    fun `headerless decimal comma regression`() {
        val page = createPage(
            listOf(
                "000301          TOMATE ROMA    25 KG    2    31,50    63,00",
                "000302          CEBOLLA        20 KG    1    28,00    28,00",
                "000303          POLLO          15 KG    3    54,25   162,75"
            )
        )
        val result = parser.parse(listOf(page))
        assertEquals(3, result.lines.size)
        
        val line3 = result.lines[2]
        assertEquals("000303", line3.vendorCode.normalizedValue)
        assertEquals(BigDecimal("162.75"), line3.lineTotal.normalizedValue)
    }

    @Test
    fun `leading-zero SKU preservation`() {
        val page = createPage(
            listOf(
                "ITEM           DESCRIPTION  QTY  TOTAL",
                "000101         TOMATO       1    10.00"
            )
        )
        val result = parser.parse(listOf(page))
        assertEquals("000101", result.lines[0].vendorCode.normalizedValue)
    }

    @Test
    fun `determinism test`() {
        val page = createPage(listOf(
            "101   ITEM A   1   10.00   10.00",
            "102   ITEM B   1   15.00   15.00",
            "103   ITEM C   1   20.00   20.00"
        ))
        val result1 = parser.parse(listOf(page))
        val result2 = parser.parse(listOf(page))
        
        assertEquals(result1.lines.size, result2.lines.size)
        assertEquals(result1.lines[0].description.normalizedValue, result2.lines[0].description.normalizedValue)
        assertEquals(result1.confidence, result2.confidence)
    }

    private fun createPage(lines: List<String>): OcrPageEvidence {
        val ocrLines = lines.mapIndexed { lineIdx, text ->
            var lastIndex = 0
            val elements = text.split(Regex("\\s{3,}")).filter { it.isNotBlank() }.map { elemText ->
                val startIdx = text.indexOf(elemText, lastIndex)
                lastIndex = startIdx + elemText.length
                OcrElementEvidence(
                    text = elemText,
                    boundingBox = OcrRect(
                        left = startIdx * 10,
                        top = lineIdx * 50,
                        right = (startIdx + elemText.length) * 10,
                        bottom = (lineIdx + 1) * 50
                    )
                )
            }
            OcrLineEvidence(text = text, boundingBox = null, elements = elements)
        }
        val pageText = lines.joinToString("\n")
        return OcrPageEvidence(
            text = pageText,
            blocks = listOf(OcrBlockEvidence(
                text = pageText, 
                boundingBox = null, 
                cornerPoints = emptyList(), 
                recognizedLanguages = emptyList(), 
                lines = ocrLines
            )),
            widthPx = 1000,
            heightPx = 1000
        )
    }
}
