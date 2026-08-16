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
                "Qty\tItem\tDescription\tRate\tAmount",
                "6\tDSALAM1",
                "\t\tCITERIO GENOA SALAMI 3/6lb",
                "\t\t\t4.68\t28.08",
                "1\tFPLATMAYA",
                "\t\tMAYA SWEET PLANTAIN 24 LB",
                "\t\tUPC5265800837",
                "\t\t\t32.00\t32.00",
                "1\tFYUC000",
                "\t\tCARIBBEAN BEST YUCA 6/5 LBS",
                "\t\t\t48.24\t48.24"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(3, result.lines.size)

        // Product 1
        val line1 = result.lines[0]
        assertEquals("DSALAM1", line1.vendorCode.normalizedValue)
        assertTrue(line1.description.normalizedValue?.contains("CITERIO GENOA SALAMI") == true)
        assertEquals(0, BigDecimal("6").compareTo(line1.quantity.normalizedValue))
        assertEquals(0, BigDecimal("4.68").compareTo(line1.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("28.08").compareTo(line1.lineTotal.normalizedValue))

        // Product 2
        val line2 = result.lines[1]
        assertEquals("FPLATMAYA", line2.vendorCode.normalizedValue)
        assertTrue(line2.description.normalizedValue?.contains("MAYA SWEET PLANTAIN") == true)
        assertEquals("24 LB", line2.packageText.normalizedValue)
        assertEquals(0, BigDecimal("1").compareTo(line2.quantity.normalizedValue))
        assertEquals(0, BigDecimal("32.00").compareTo(line2.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("32.00").compareTo(line2.lineTotal.normalizedValue))

        // Product 3
        val line3 = result.lines[2]
        assertEquals("FYUC000", line3.vendorCode.normalizedValue)
        assertTrue(line3.description.normalizedValue?.contains("CARIBBEAN BEST YUCA") == true)
        assertEquals(0, BigDecimal("1").compareTo(line3.quantity.normalizedValue))
        assertEquals(0, BigDecimal("48.24").compareTo(line3.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("48.24").compareTo(line3.lineTotal.normalizedValue))
    }

    @Test
    fun `summary isolation label plus detached value`() {
        val page = createPage(
            listOf(
                "ITEM\tDESCRIPTION\tAMOUNT",
                "A\tProductA\t10.00",
                "B\tProductB\t20.00",
                "Subtotal",
                "\t\t30.00",
                "Tax",
                "\t\t2.10",
                "Total",
                "\t\t32.10"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals(0, BigDecimal("30.00").compareTo(result.subtotal.normalizedValue))
        assertEquals(0, BigDecimal("2.10").compareTo(result.tax.normalizedValue))
        assertEquals(0, BigDecimal("32.10").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `total cereal remains product`() {
        val page = createPage(
            listOf(
                "DESCRIPTION\tAMOUNT",
                "TOTAL CEREAL\t18.00",
                "RICE\t5.00",
                "TOTAL\t23.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertTrue(result.lines[0].description.normalizedValue?.contains("TOTAL CEREAL") == true)
        assertEquals(0, BigDecimal("18.00").compareTo(result.lines[0].lineTotal.normalizedValue))
        assertEquals(0, BigDecimal("23.00").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `taxi sauce and coffee beans safety`() {
        val page = createPage(
            listOf(
                "DESCRIPTION\tAMOUNT",
                "TAXI SAUCE\t7.50",
                "COFFEE BEANS\t12.00",
                "TOTAL\t19.50"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertTrue(result.lines[0].description.normalizedValue?.contains("TAXI SAUCE") == true)
        assertTrue(result.lines[1].description.normalizedValue?.contains("COFFEE BEANS") == true)
        assertEquals(0, BigDecimal("19.50").compareTo(result.total.normalizedValue))
        // Verify no false tax or fee
        assertNull(result.tax.normalizedValue)
        assertNull(result.fees.normalizedValue)
    }

    @Test
    fun `structured fuel charge remains product`() {
        val page = createPage(
            listOf(
                "QTY\tDESCRIPTION\tRATE\tAMOUNT",
                "1\tFUEL CHARGE\t5.00\t5.00",
                "TOTAL\t\t\t5.00"
            )
        )

        val result = parser.parse(listOf(page))

        assertEquals(1, result.lines.size)
        assertTrue(result.lines[0].description.normalizedValue?.contains("FUEL CHARGE") == true)
        assertEquals(0, BigDecimal("5.00").compareTo(result.lines[0].lineTotal.normalizedValue))
    }

    @Test
    fun `page total on p1 and total on p2 correctly identified`() {
        val p1 = createPage(listOf(
            "ITEM\tDESCRIPTION\tAMOUNT",
            "101\tITEM_A\t10.00",
            "PAGE TOTAL\t10.00"
        ))
        
        val p2 = createPage(listOf(
            "ITEM\tDESCRIPTION\tAMOUNT",
            "102\tITEM_B\t20.00",
            "TOTAL\t30.00"
        ))
        
        val result = parser.parse(listOf(p1, p2))
        
        assertEquals(2, result.lines.size)
        assertEquals(0, BigDecimal("30.00").compareTo(result.total.normalizedValue))
    }

    @Test
    fun `incomplete product A does not swallow complete product B`() {
        val page = createPage(listOf(
            "Qty\tItem\tDescription\tRate\tAmount",
            "1\tABC\tPRODUCT A",
            "1\tXYZ\tPRODUCT B\t10.00\t10.00"
        ))
        
        val result = parser.parse(listOf(page))
        
        assertEquals(2, result.lines.size)
        val lineA = result.lines.find { it.description.normalizedValue?.contains("PRODUCT A") == true }
        val lineB = result.lines.find { it.description.normalizedValue?.contains("PRODUCT B") == true }
        
        assertNotNull(lineA)
        assertNotNull(lineB)
        
        // PRODUCT A should not have PRODUCT B's money
        assertNull(lineA?.lineTotal?.normalizedValue)
        assertEquals(0, BigDecimal("10.00").compareTo(lineB?.lineTotal?.normalizedValue))
    }

    @Test
    fun `accounting parentheses negative money parsing`() {
        val page = createPage(listOf(
            "ITEM\tDESCRIPTION\tAMOUNT",
            "101\tITEM A\t$10.00",
            "102\tDISCOUNT\t($4.30)",
            "103\tCREDIT\t(1.00)",
            "104\tADJUST\t-$2.00",
            "TOTAL\t\t$2.70"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(4, result.lines.size)
        assertEquals(0, BigDecimal("10.00").compareTo(result.lines[0].lineTotal.normalizedValue))
        assertEquals(0, BigDecimal("-4.30").compareTo(result.lines[1].lineTotal.normalizedValue))
        assertEquals(0, BigDecimal("-1.00").compareTo(result.lines[2].lineTotal.normalizedValue))
        assertEquals(0, BigDecimal("-2.00").compareTo(result.lines[3].lineTotal.normalizedValue))
    }

    @Test
    fun `fractional quantities 0 25 and 0 5`() {
        val page = createPage(listOf(
            "QTY\tDESCRIPTION\tRATE\tAMOUNT",
            "0.25\tCUMIN\t10.00\t2.50",
            "0.5\tWINE\t8.00\t4.00",
            "TOTAL\t\t\t6.50"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals(0, BigDecimal("0.25").compareTo(result.lines[0].quantity.normalizedValue))
        assertEquals(0, BigDecimal("0.5").compareTo(result.lines[1].quantity.normalizedValue))
    }

    @Test
    fun `complete product plus money-only orphan`() {
        val page = createPage(listOf(
            "Qty\tItem\tDescription\tRate\tAmount",
            "1\tABC\tPRODUCT A\t10.00\t10.00",
            "\t\t\t\t5.00",
            "1\tXYZ\tPRODUCT B\t20.00\t20.00"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        val lineA = result.lines.find { it.vendorCode.normalizedValue == "ABC" }
        val lineB = result.lines.find { it.vendorCode.normalizedValue == "XYZ" }

        assertNotNull(lineA)
        assertNotNull(lineB)

        assertEquals(0, BigDecimal("10.00").compareTo(lineA!!.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("10.00").compareTo(lineA.lineTotal.normalizedValue))

        assertEquals(0, BigDecimal("20.00").compareTo(lineB!!.unitPrice.normalizedValue))
        assertEquals(0, BigDecimal("20.00").compareTo(lineB.lineTotal.normalizedValue))
    }

    @Test
    fun `complete product plus random text orphan`() {
        val page = createPage(listOf(
            "Qty\tItem\tDescription\tRate\tAmount",
            "1\tABC\tCHICKEN\t10.00\t10.00",
            "\t\tRANDOM TEXT",
            "1\tXYZ\tRICE\t20.00\t20.00"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        val lineA = result.lines.find { it.description.normalizedValue?.contains("CHICKEN") == true }
        val lineB = result.lines.find { it.description.normalizedValue?.contains("RICE") == true }

        assertNotNull(lineA)
        assertNotNull(lineB)

        assertFalse(lineA!!.description.normalizedValue?.contains("RANDOM TEXT") == true)
        assertTrue(lineA.description.normalizedValue == "CHICKEN")
    }

    @Test
    fun `adjacent complete products`() {
        val page = createPage(listOf(
            "Qty\tItem\tDescription\tRate\tAmount",
            "1\tABC\tITEM A\t10.00\t10.00",
            "1\tXYZ\tITEM B\t20.00\t20.00"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertEquals("ITEM A", result.lines[0].description.normalizedValue)
        assertEquals("ITEM B", result.lines[1].description.normalizedValue)
    }

    @Test
    fun `page subtotal and carried forward ignored as products`() {
        val page = createPage(listOf(
            "DESCRIPTION\tAMOUNT",
            "ITEM A\t10.00",
            "PAGE SUBTOTAL\t10.00",
            "CARRIED FORWARD\t10.00",
            "ITEM B\t20.00",
            "TOTAL\t30.00"
        ))

        val result = parser.parse(listOf(page))

        assertEquals(2, result.lines.size)
        assertTrue(result.lines.any { it.description.normalizedValue == "ITEM A" })
        assertTrue(result.lines.any { it.description.normalizedValue == "ITEM B" })
        assertEquals(0, BigDecimal("30.00").compareTo(result.total.normalizedValue))
    }

    private fun createPage(lines: List<String>): OcrPageEvidence {
        val ocrLines = lines.mapIndexed { lineIdx, text ->
            val elements = mutableListOf<OcrElementEvidence>()
            // Split by tabs to simulate exact column hits
            val parts = text.split("\t")
            var currentPos = 0
            for (part in parts) {
                if (part.isNotBlank()) {
                    val rect = OcrRect(
                        left = currentPos * 100,
                        top = lineIdx * 50,
                        right = (currentPos + 1) * 100,
                        bottom = (lineIdx + 1) * 50
                    )
                    elements.add(OcrElementEvidence(text = part.trim(), boundingBox = rect))
                }
                currentPos++
            }
            OcrLineEvidence(text = text.replace("\t", " "), boundingBox = null, elements = elements)
        }
        val pageText = lines.joinToString("\n").replace("\t", " ")
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
