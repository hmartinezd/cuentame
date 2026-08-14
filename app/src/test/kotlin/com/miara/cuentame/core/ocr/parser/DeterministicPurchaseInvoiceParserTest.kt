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
                "ITEM           DESCRIPTION   QTY   TOTAL",
                "000101         TOMATO        1     10.00"
            )
        )
        val result = parser.parse(listOf(page))
        assertEquals("000101", result.lines[0].vendorCode.normalizedValue)
    }

    @Test
    fun `payment rows after terminal total are outside item table`() {
        val result = parser.parse(listOf(createPage(listOf(
            "DESCRIPTION      QTY      PRICE      TOTAL",
            "ITEM A           1        10.00      10.00",
            "ITEM B           1         5.00       5.00",
            "SUBTOTAL                              15.00",
            "TAX                                    1.05",
            "TOTAL                                 16.05",
            "VISA                                  16.05",
            "PAID                                  16.05",
            "AUTH 123456"
        ))))

        assertEquals(listOf("ITEM A", "ITEM B"), result.lines.map { it.description.normalizedValue })
    }

    @Test
    fun `product whose description starts with total does not terminate table`() {
        val result = parser.parse(listOf(createPage(listOf(
            "DESCRIPTION      QTY      PRICE      TOTAL",
            "CHICKEN BREAST   2        5.00       10.00",
            "TOTAL CEREAL     1        4.00        4.00",
            "ONION            3        2.00        6.00",
            "TOTAL                                 20.00",
            "VISA                                  20.00"
        ))))

        assertEquals(listOf("CHICKEN BREAST", "TOTAL CEREAL", "ONION"),
            result.lines.map { it.description.normalizedValue })
    }

    @Test
    fun `description amount receipt establishes repeated item structure`() {
        val result = parser.parse(listOf(createPage(listOf(
            "DESCRIPTION                         AMOUNT",
            "CHICKEN BREAST                      10.00",
            "RICE                                 5.00",
            "BEANS                                4.50",
            "TOTAL                               19.50",
            "PAID                                19.50"
        ))))

        assertEquals(listOf("CHICKEN BREAST", "RICE", "BEANS"),
            result.lines.map { it.description.normalizedValue })
    }

    @Test
    fun `terminal total requires period and exactly two decimals`() {
        val result = parser.parse(listOf(createPage(listOf(
            "DESCRIPTION      QTY      PRICE      TOTAL",
            "ITEM A           1        10.00      10.00",
            "TOTAL                                 10.0",
            "ITEM B           1         5.00       5.00",
            "TOTAL                                15.00",
            "VISA                                 15.00"
        ))))

        assertEquals(listOf("ITEM A", "ITEM B"), result.lines.map { it.description.normalizedValue })
    }

    @Test
    fun `ambiguous orphan row does not contaminate either product`() {
        val result = parser.parse(listOf(createPage(listOf(
            "DESCRIPTION      QTY      PRICE      TOTAL",
            "ITEM A           1        10.00      10.00",
            "EXTRA DESCRIPTION",
            "ITEM B           1         5.00       5.00"
        ))))

        assertEquals("ITEM A", result.lines[0].description.normalizedValue)
        assertEquals("ITEM B", result.lines[1].description.normalizedValue)
    }

    @Test
    fun `separate reversed blocks reconstruct description amount rows`() {
        val descriptions = block(listOf("Chicago Italian Bread" to OcrRect(50, 100, 300, 125), "French Bread 12oz" to OcrRect(50, 150, 260, 175)))
        val prices = block(listOf("13.29" to OcrRect(800, 101, 870, 126), "2.49" to OcrRect(800, 151, 860, 176)))
        val page = OcrPageEvidence(1000, 1000, "", listOf(prices, descriptions))

        val result = parser.parse(listOf(page))

        assertEquals(listOf("Chicago Italian Bread", "French Bread"), result.lines.map { it.description.normalizedValue })
        assertEquals(listOf(BigDecimal("13.29"), BigDecimal("2.49")), result.lines.map { it.lineTotal.normalizedValue })
    }

    @Test
    fun `document total cuts off later physical pages`() {
        val first = createPage(listOf("DESCRIPTION      AMOUNT", "ITEM A           10.00", "ITEM B           10.00", "TOTAL            20.00", "VISA             20.00"))
        val second = createPage(listOf("PAYMENT TERMS", "NET 30", "ACCOUNT 12345", "SIGNATURE"))
        val result = parser.parse(listOf(first, second))
        assertEquals(listOf("ITEM A", "ITEM B"), result.lines.map { it.description.normalizedValue })
        assertEquals(BigDecimal("20.00"), result.total.normalizedValue)
    }

    @Test
    fun `split package elements do not become quantity`() {
        val result = parser.parse(listOf(createPage(listOf(
            "CHICKEN BREAST   2   10   LB   13.29",
            "TOMATOES         1   20   LB    5.00",
            "ONIONS           3   10   LB    6.00"
        ))))
        assertEquals(BigDecimal("2"), result.lines.first().quantity.normalizedValue)
        assertEquals("10 LB", result.lines.first().packageText.normalizedValue)
    }

    @Test
    fun `layout normalization preserves confidence and excludes geometryless evidence`() {
        val visible = OcrElementEvidence("ITEM", OcrRect(10, 20, 80, 40), confidence = 0.37f)
        val geometryless = OcrElementEvidence("RAW ONLY", null, confidence = 0.99f)
        val page = OcrPageEvidence(1000, 1000, "ITEM RAW ONLY", listOf(OcrBlockEvidence(
            "", null, emptyList(), emptyList(), listOf(OcrLineEvidence("", null, elements = listOf(visible, geometryless)))
        )))

        val tokens = parser.normalizeLayoutTokens(listOf(page))

        assertEquals(1, tokens.size)
        assertEquals("ITEM", tokens.single().text)
        assertEquals(0.37f, tokens.single().ocrConfidence)
    }

    @Test
    fun `headerless receipt does not invent SKU from description`() {
        val result = parser.parse(listOf(createPage(listOf(
            "TOMATO ROMA      2      3.00      6.00",
            "ONION YELLOW     1      4.00      4.00",
            "POTATO RED       3      2.00      6.00"
        ))))

        assertEquals(3, result.lines.size)
        assertTrue(result.lines.all { it.vendorCode.normalizedValue == null })
        assertEquals("TOMATO ROMA", result.lines.first().description.normalizedValue)
    }

    @Test
    fun `headerless quantity first layout uses evidence instead of leftmost position`() {
        val result = parser.parse(listOf(createPage(listOf(
            "2      CHICKEN BREAST      10 LB      4.50      9.00",
            "1      TOMATOES            20 LB      3.00      3.00",
            "3      ONIONS              10 LB      2.00      6.00"
        ))))

        assertEquals(3, result.lines.size)
        assertTrue(result.lines.all { it.vendorCode.normalizedValue == null })
        assertEquals(listOf("CHICKEN BREAST", "TOMATOES", "ONIONS"),
            result.lines.map { it.description.normalizedValue })
        assertEquals(listOf(BigDecimal("2"), BigDecimal("1"), BigDecimal("3")),
            result.lines.map { it.quantity.normalizedValue })
        assertEquals(listOf(BigDecimal("4.50"), BigDecimal("3.00"), BigDecimal("2.00")),
            result.lines.map { it.unitPrice.normalizedValue })
        assertEquals(listOf(BigDecimal("9.00"), BigDecimal("3.00"), BigDecimal("6.00")),
            result.lines.map { it.lineTotal.normalizedValue })
    }

    @Test
    fun `headerless repeated numeric SKU preserves leading zeros`() {
        val result = parser.parse(listOf(createPage(listOf(
            "000101      TOMATO      2      3.00      6.00",
            "000102      ONION       1      4.00      4.00",
            "000103      POTATO      3      2.00      6.00"
        ))))

        assertEquals(listOf("000101", "000102", "000103"),
            result.lines.map { it.vendorCode.normalizedValue })
    }

    @Test
    fun `headerless repeated alphanumeric SKU is recognized`() {
        val result = parser.parse(listOf(createPage(listOf(
            "AB-101      TOMATO      2      3.00      6.00",
            "AB-102      ONION       1      4.00      4.00",
            "AB-103      POTATO      3      2.00      6.00"
        ))))

        assertEquals(listOf("AB-101", "AB-102", "AB-103"),
            result.lines.map { it.vendorCode.normalizedValue })
        assertEquals(listOf(BigDecimal("2"), BigDecimal("1"), BigDecimal("3")),
            result.lines.map { it.quantity.normalizedValue })
    }

    @Test
    fun `unknown single item does not absorb preceding preamble`() {
        val result = parser.parse(listOf(createPage(listOf(
            "THANK YOU FOR YOUR BUSINESS",
            "CHICKEN BREAST      1      5.00      5.00",
            "TOTAL                            5.00"
        ))))

        assertTrue(result.warnings.contains(InvoiceParseWarning.UnknownColumnLayout))
        assertEquals("CHICKEN BREAST", result.lines.single().description.normalizedValue)
    }

    @Test
    fun `text before semantic table is not prepended to first item`() {
        val result = parser.parse(listOf(createPage(listOf(
            "THANK YOU FOR YOUR BUSINESS",
            "ITEM             DESCRIPTION      QTY      PRICE      TOTAL",
            "001              TOMATO           1        5.00       5.00"
        ))))

        assertEquals("TOMATO", result.lines.single().description.normalizedValue)
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

    private fun block(values: List<Pair<String, OcrRect>>) = OcrBlockEvidence(
        text = values.joinToString("\n") { it.first }, boundingBox = null, cornerPoints = emptyList(),
        recognizedLanguages = emptyList(), lines = values.map { (text, rect) ->
            OcrLineEvidence(text, rect, elements = listOf(OcrElementEvidence(text, rect)))
        }
    )
}
