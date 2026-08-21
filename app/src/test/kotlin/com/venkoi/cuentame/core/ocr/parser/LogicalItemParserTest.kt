package com.venkoi.cuentame.core.ocr.parser

import com.venkoi.cuentame.core.ocr.api.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LogicalItemParserTest {

    private val parser = DeterministicPurchaseInvoiceParser()

    @Test
    fun `JC Foods 3-product fixture multi-row assembly`() {
        val page = createPage(
            listOf(
                "Qty\tItem\tDescription\tRate\tAmount",
                "6\tDSALAM1",
                "\t\tCITERIO GENOA SALAMI",
                "\t\t\t4.68\t28.08",
                "1\tFPLATMAYA",
                "\t\tMAYA SWEET PLANTAIN",
                "\t\tUPC5265800837",
                "\t\t\t32.00\t32.00",
                "1\tFYUC000",
                "\t\tCARIBBEAN BEST YUCA",
                "\t\t\t48.24\t48.24"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(3, result.lines.size)
    }

    @Test
    fun `summary isolation label plus detached value`() {
        val page = createPage(
            listOf(
                "ITEM     DESCRIPTION       AMOUNT",
                "A        ProductA          10.00",
                "B        ProductB          20.00",
                "Subtotal",
                "                           30.00",
                "Total",
                "                           30.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertNotNull(result.subtotal.normalizedValue)
    }

    @Test
    fun `total cereal remains product`() {
        val page = createPage(
            listOf(
                "DESCRIPTION           AMOUNT",
                "TOTAL_CEREAL_10LB     18.00",
                "RICE                   5.00",
                "TOTAL                 23.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertTrue(result.lines[0].description.normalizedValue?.contains("TOTAL_CEREAL") == true)
        assertEquals(0, BigDecimal("23.00").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `page total on p1 and total on p2 correctly identified`() {
        val p1 = createPage(listOf(
            "ITEM   DESCRIPTION   AMOUNT",
            "101    ITEM_A        10.00",
            "103    ITEM_C        15.00",
            "PAGE TOTAL           25.00"
        ))
        
        val p2 = createPage(listOf(
            "ITEM   DESCRIPTION   AMOUNT",
            "102    ITEM_B        20.00",
            "104    ITEM_D        30.00",
            "TOTAL                65.00"
        ))
        
        val result = parser.parse(listOf(p1, p2))
        
        assertEquals(4, result.lines.size)
        assertEquals(0, BigDecimal("65.00").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `incomplete product A does not swallow complete product B`() {
        val page = createPage(listOf(
            "Qty   Item   Description   Rate   Amount",
            "1     ABC    PRODUCTA",
            "1     XYZ    PRODUCTB      10.00  10.00"
        ))
        
        val result = parser.parse(listOf(page))
        
        assertEquals(2, result.lines.size)
    }

    @Test
    fun `intermediate summaries CARRIED FORWARD and PAGE SUBTOTAL safety`() {
        val page = createPage(listOf(
            "ITEM   DESCRIPTION   AMOUNT",
            "101    ITEM_A        10.00",
            "PAGE SUBTOTAL        10.00",
            "CARRIED FORWARD      10.00",
            "102    ITEM_B        20.00",
            "TOTAL                30.00"
        ))
        
        val result = parser.parse(listOf(page))
        
        assertEquals(2, result.lines.size)
        assertEquals(0, BigDecimal("30.00").compareTo(result.total.normalizedValue))
        // Intermediate values should not populate result fields
        assertNull(result.subtotal.normalizedValue) 
    }

    private fun createPage(lines: List<String>): OcrPageEvidence {
        val ocrLines = lines.mapIndexed { lineIdx, text ->
            val elements = mutableListOf<OcrElementEvidence>()
            val matcher = Regex("\\S+").findAll(text)
            for (match in matcher) {
                val elemText = match.value
                val startIdx = match.range.first
                elements.add(OcrElementEvidence(
                    text = elemText,
                    boundingBox = OcrRect(
                        left = startIdx * 10,
                        top = lineIdx * 50,
                        right = (startIdx + elemText.length) * 10,
                        bottom = (lineIdx + 1) * 50
                    )
                ))
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
