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
                "000101   TOMATO ROMA        25 LB CS     2    31.50    63.00",
                "000102   ONION YELLOW       50 LB        1    28.00    28.00",
                "000103   CHICKEN BREAST     40 LB CS     3    54.25   162.75"
            )
        )

        val result = parser.parse(listOf(page))

        assertTrue("Should have InferredColumnLayout warning", result.warnings.contains(InvoiceParseWarning.InferredColumnLayout))
        assertEquals(3, result.lines.size)

        val line1 = result.lines[0]
        assertEquals("000101", line1.vendorCode.normalizedValue)
        assertTrue("Description should contain TOMATO ROMA", line1.description.normalizedValue?.contains("TOMATO ROMA") == true)
        assertEquals(BigDecimal("2"), line1.quantity.normalizedValue)
        assertEquals(BigDecimal("31.50"), line1.unitPrice.normalizedValue)
        assertEquals(BigDecimal("63.00"), line1.lineTotal.normalizedValue)
    }

    @Test
    fun `financial precision 0_10 times 3 equals 0_30`() {
        val page = createPage(
            listOf(
                "ITEM  DESCRIPTION  QTY  PRICE  AMOUNT",
                "101   CANDY        3    0.10   0.30"
            )
        )

        val result = parser.parse(listOf(page))
        val line = result.lines[0]

        assertFalse("Should not have LineMathMismatch warning", line.warnings.contains(InvoiceParseWarning.LineMathMismatch))
        assertEquals(BigDecimal("0.30"), line.lineTotal.normalizedValue)
    }

    @Test
    fun `headerless with noise above and below`() {
        val page = createPage(
            listOf(
                "SUPPLIER NAME",
                "123 MAIN ST",
                "INVOICE: 12345",
                "DATE: 2026-08-08",
                "",
                "000101   TOMATO ROMA        2    31.50    63.00",
                "000102   ONION YELLOW       1    28.00    28.00",
                "",
                "Subtotal       91.00",
                "Tax              0.00",
                "Total          91.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals("SUPPLIER NAME", result.supplierNameCandidate.normalizedValue)
        assertEquals("12345", result.invoiceNumber.normalizedValue)
        assertEquals(BigDecimal("91.00"), result.total.normalizedValue)
        
        // Ensure totals are not products
        assertFalse("Totals should not be treated as lines", result.lines.any { it.description.normalizedValue?.lowercase()?.contains("total") == true })
    }

    @Test
    fun `headerless wrapped description`() {
        val page = createPage(
            listOf(
                "000201   CHICKEN BREAST BONELESS",
                "         SKINLESS FROZEN",
                "                                  2    74.50    149.00"
            )
        )

        val result = parser.parse(listOf(page))
        
        // This heuristic-heavy case.
        assertTrue("Should extract at least one line", result.lines.isNotEmpty())
        assertTrue("Description should be partially or fully captured", 
            result.lines[0].description.normalizedValue?.contains("CHICKEN BREAST") == true)
        assertEquals(BigDecimal("149.00"), result.lines[0].lineTotal.normalizedValue)
    }

    @Test
    fun `multipage continuation without repeated header`() {
        val page1 = createPage(
            listOf(
                "ITEM   DESCRIPTION   QTY   PRICE   TOTAL",
                "101    ITEM A        1     10.00   10.00"
            )
        )
        val page2 = createPage(
            listOf(
                "102    ITEM B        2     15.00   30.00"
            )
        )

        val result = parser.parse(listOf(page1, page2))

        assertEquals(2, result.lines.size)
        assertEquals("101", result.lines[0].vendorCode.normalizedValue)
        assertEquals("102", result.lines[1].vendorCode.normalizedValue)
    }

    private fun createPage(lines: List<String>): OcrPageEvidence {
        val ocrLines = lines.mapIndexed { lineIdx, text ->
            val elements = text.split(Regex("\\s{2,}|(?<=\\d)\\s(?=\\d)")).filter { it.isNotBlank() }.map { elemText ->
                val startIdx = text.indexOf(elemText)
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
