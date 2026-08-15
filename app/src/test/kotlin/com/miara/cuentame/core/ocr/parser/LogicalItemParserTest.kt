package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LogicalItemParserTest {

    private val parser = DeterministicPurchaseInvoiceParser()

    @Test
    fun `JC Foods 3-product fixture multi-row assembly`() {
        val page = createPage(
            listOf(
                "Qty      Item         Description                 Rate     Amount",
                "",
                "6        DSALAM1",
                "         CITERIO GENOA SALAMI 3/6lb",
                "                                                  4.68     28.08",
                "",
                "1        FPLATMAYA...",
                "         MAYA SWEET PLANTAIN 24 LB",
                "",
                "         UPC5265800837",
                "",
                "                                                  32.00    32.00",
                "",
                "1        FYUC000",
                "         CARIBBEAN BEST YUCA 6/5 LBS",
                "                                                  48.24    48.24"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(3, result.lines.size)

        // Line 1: CITERIO GENOA SALAMI
        val line1 = result.lines[0]
        assertTrue("Desc was: ${line1.description.normalizedValue}", 
            line1.description.normalizedValue?.contains("CITERIO GENOA SALAMI") == true)
        assertEquals(0, BigDecimal("6").compareTo(line1.quantity.normalizedValue))
        assertEquals(0, BigDecimal("4.68").compareTo(line1.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("28.08").compareTo(line1.lineTotal.normalizedValue))

        // Line 2: MAYA SWEET PLANTAIN
        val line2 = result.lines[1]
        assertTrue("Desc was: ${line2.description.normalizedValue}",
            line2.description.normalizedValue?.contains("MAYA SWEET PLANTAIN") == true)
        assertEquals(0, BigDecimal("1").compareTo(line2.quantity.normalizedValue))
        assertEquals(0, BigDecimal("32.00").compareTo(line2.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("32.00").compareTo(line2.lineTotal.normalizedValue))

        // Line 3: CARIBBEAN BEST YUCA
        val line3 = result.lines[2]
        assertTrue("Desc was: ${line3.description.normalizedValue}",
            line3.description.normalizedValue?.contains("CARIBBEAN BEST YUCA") == true)
        assertEquals(0, BigDecimal("1").compareTo(line3.quantity.normalizedValue))
        assertEquals(0, BigDecimal("48.24").compareTo(line3.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("48.24").compareTo(line3.lineTotal.normalizedValue))
    }

    @Test
    fun `summary isolation label plus detached value`() {
        val page = createPage(
            listOf(
                "Product A                       10.00",
                "Product B                       20.00",
                "",
                "Subtotal",
                "                                30.00",
                "",
                "Tax",
                "                                 2.10",
                "",
                "Total",
                "                                32.10"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals(0, BigDecimal("30.00").compareTo(result.subtotal.normalizedValue))
        assertEquals(0, BigDecimal("2.10").compareTo(result.tax.normalizedValue))
        assertEquals(0, BigDecimal("32.10").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `fractional quantities preserved`() {
        val page = createPage(
            listOf(
                "0.25     CUMIN WHOLE SEED         10.00    2.50",
                "0.5      COOKING WINE              8.00    4.00",
                "         TOTAL                            6.50"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertNotNull("Line 1 quantity was null", result.lines[0].quantity.normalizedValue)
        assertEquals(0, BigDecimal("0.25").compareTo(result.lines[0].quantity.normalizedValue))
        assertNotNull("Line 2 quantity was null", result.lines[1].quantity.normalizedValue)
        assertEquals(0, BigDecimal("0.5").compareTo(result.lines[1].quantity.normalizedValue))
    }

    @Test
    fun `total cereal remains product`() {
        val page = createPage(
            listOf(
                "1        TOTAL CEREAL 10 LB       18.00    18.00",
                "TOTAL                             18.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(1, result.lines.size)
        assertEquals("TOTAL CEREAL 10 LB", result.lines[0].description.normalizedValue)
        assertEquals(0, BigDecimal("18.00").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `structured fuel charge is not discarded`() {
        val page = createPage(
            listOf(
                "1        FUEL CHARGE              5.00     5.00",
                "TOTAL                             5.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(1, result.lines.size)
        assertTrue(result.lines[0].description.normalizedValue?.contains("FUEL CHARGE") == true)
    }

    @Test
    fun `adjacent normal products remain separate`() {
        val page = createPage(
            listOf(
                "1        ITEM A                   10.00    10.00",
                "1        ITEM B                   20.00    20.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals("ITEM A", result.lines[0].description.normalizedValue)
        assertEquals("ITEM B", result.lines[1].description.normalizedValue)
    }

    @Test
    fun `ambiguous continuation remains unresolved`() {
        val page = createPage(
            listOf(
                "1        ITEM A                   10.00    10.00",
                "RANDOM TEXT WITHOUT MONEY",
                "1        ITEM B                   20.00    20.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals("ITEM A", result.lines[0].description.normalizedValue)
        assertEquals("ITEM B", result.lines[1].description.normalizedValue)
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
