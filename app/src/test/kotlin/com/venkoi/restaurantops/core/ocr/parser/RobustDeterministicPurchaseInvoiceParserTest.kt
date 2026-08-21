package com.venkoi.restaurantops.core.ocr.parser

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.ocr.api.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RobustDeterministicPurchaseInvoiceParserTest {

    private val parser = DeterministicPurchaseInvoiceParser()

    @Test
    fun `manual missing line has no fabricated OCR provenance`() {
        val line = ParsedInvoiceLineCandidate.manual(7)

        assertThat(line.index).isEqualTo(7)
        assertThat(line.evidenceRefs).isEmpty()
        assertThat(line.confidence).isNull()
        assertThat(line.description.detectedText).isNull()
        assertThat(line.description.evidenceRefs).isEmpty()
    }

    @Test
    fun `standard US distributor invoice with table`() {
        val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "SYSCO FOODS\nITEM DESCRIPTION QTY PRICE AMOUNT\n001234 TOMATO ROMA 2 31.50 63.00",
                blocks = listOf(
                    // Supplier
                    textBlock("SYSCO FOODS", 100, 100, 300, 150),
                    // Table Header
                    headerBlock(listOf("ITEM", "DESCRIPTION", "QTY", "PRICE", "AMOUNT"), 200),
                    // Table Row
                    lineBlock(listOf("001234", "TOMATO ROMA", "2", "31.50", "63.00"), 300)
                )
            )
        )

        val result = parser.parse(pages)

        assertThat(result.supplierNameCandidate.normalizedValue).isEqualTo("SYSCO FOODS")
        assertThat(result.lines).hasSize(1)
        val line = result.lines[0]
        assertThat(line.vendorCode.normalizedValue).isEqualTo("001234")
        assertThat(line.description.normalizedValue).isEqualTo("TOMATO ROMA")
        assertThat(line.quantity.normalizedValue).isEqualTo(BigDecimal("2"))
        assertThat(line.unitPrice.normalizedValue).isEqualTo(BigDecimal("31.50"))
        assertThat(line.lineTotal.normalizedValue).isEqualTo(BigDecimal("63.00"))
        assertThat(line.warnings).isEmpty()
    }

    @Test
    fun `Spanish headers and date`() {
        val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "DISTRIBUIDORA VENKOI\n8 de agosto de 2026\nCÓDIGO DESCRIPCIÓN CANTIDAD PRECIO IMPORTE\n1001 PAPA ROJA 5 12,50 62,50",
                blocks = listOf(
                    textBlock("DISTRIBUIDORA VENKOI", 100, 100, 400, 150),
                    textBlock("8 de agosto de 2026", 100, 160, 400, 200),
                    headerBlock(listOf("CÓDIGO", "DESCRIPCIÓN", "CANTIDAD", "PRECIO", "IMPORTE"), 300),
                    lineBlock(listOf("1001", "PAPA ROJA", "5", "12,50", "62,50"), 400)
                )
            )
        )

        val result = parser.parse(pages)

        assertThat(result.invoiceDate.normalizedValue).isEqualTo(LocalDate.of(2026, 8, 8))
        assertThat(result.lines).hasSize(1)
        val line = result.lines[0]
        assertThat(line.description.normalizedValue).isEqualTo("PAPA ROJA")
        assertThat(line.quantity.normalizedValue).isEqualTo(BigDecimal("5"))
        assertThat(line.unitPrice.normalizedValue).isEqualTo(BigDecimal("12.50"))
        assertThat(line.lineTotal.normalizedValue).isEqualTo(BigDecimal("62.50"))
    }

    @Test
    fun `wrapped description`() {
        val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "ITEM DESCRIPTION QTY PRICE AMOUNT\n001234 CHICKEN BREAST\nBONELESS SKINLESS 2 74.50 149.00",
                blocks = listOf(
                    headerBlock(listOf("ITEM", "DESCRIPTION", "QTY", "PRICE", "AMOUNT"), 100),
                    lineBlock(listOf("001234", "CHICKEN BREAST"), 200),
                    // Continuation row - align with columns
                    lineBlock(listOf("", "BONELESS SKINLESS", "2", "74.50", "149.00"), 230)
                )
            )
        )

        val result = parser.parse(pages)

        assertThat(result.lines).hasSize(1)
        assertThat(result.lines[0].description.normalizedValue).contains("BONELESS SKINLESS")
        assertThat(result.lines[0].lineTotal.normalizedValue).isEqualTo(BigDecimal("149.00"))
    }

    @Test
    fun `math mismatch warning`() {
        val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "ITEM DESCRIPTION QTY PRICE AMOUNT\n101 TOMATO 2 31.50 630.00",
                blocks = listOf(
                    headerBlock(listOf("ITEM", "DESCRIPTION", "QTY", "PRICE", "AMOUNT"), 100),
                    lineBlock(listOf("101", "TOMATO", "2", "31.50", "630.00"), 200)
                )
            )
        )

        val result = parser.parse(pages)
        assertThat(result.lines[0].warnings).contains(InvoiceParseWarning.LineMathMismatch)
    }

    @Test
    fun `numeric header orphan is not an item`() {
        val pages = listOf(OcrPageEvidence(
            widthPx = 1000,
            heightPx = 2000,
            text = "CHICAGO FOODS 335.96\nITEM DESCRIPTION QTY PRICE AMOUNT\n101 TOMATO 2 5.00 10.00",
            blocks = listOf(
                lineBlock(listOf("CHICAGO FOODS", "335.96"), 60),
                headerBlock(listOf("ITEM", "DESCRIPTION", "QTY", "PRICE", "AMOUNT"), 200),
                lineBlock(listOf("101", "TOMATO", "2", "5.00", "10.00"), 300)
            )
        ))

        val result = parser.parse(pages)

        assertThat(result.lines).hasSize(1)
        assertThat(result.lines.single().description.normalizedValue).isEqualTo("TOMATO")
    }

    // Helper functions to build synthetic OCR evidence
    private fun textBlock(text: String, left: Int, top: Int, right: Int, bottom: Int): OcrBlockEvidence {
        val rect = OcrRect(left, top, right, bottom)
        return OcrBlockEvidence(
            text = text,
            boundingBox = rect,
            cornerPoints = emptyList(),
            recognizedLanguages = emptyList(),
            lines = listOf(
                OcrLineEvidence(
                    text = text,
                    boundingBox = rect,
                    elements = listOf(OcrElementEvidence(text, rect))
                )
            )
        )
    }

    private fun headerBlock(cols: List<String>, top: Int): OcrBlockEvidence {
        val elements = cols.mapIndexed { i, s ->
            OcrElementEvidence(s, OcrRect(100 + i * 150, top, 200 + i * 150, top + 30))
        }
        val text = cols.joinToString(" ")
        val rect = OcrRect(100, top, 100 + cols.size * 150, top + 30)
        return OcrBlockEvidence(
            text = text,
            boundingBox = rect,
            cornerPoints = emptyList(),
            recognizedLanguages = emptyList(),
            lines = listOf(OcrLineEvidence(text, rect, elements = elements))
        )
    }

    private fun lineBlock(vals: List<String>, top: Int): OcrBlockEvidence {
        val elements = vals.mapIndexed { i, s ->
             // Try to align with header logic if possible, or just space them out
             OcrElementEvidence(s, OcrRect(100 + i * 150, top, 200 + i * 150, top + 30))
        }
        val text = vals.joinToString(" ")
        val rect = OcrRect(100, top, 100 + vals.size * 150, top + 30)
        return OcrBlockEvidence(
            text = text,
            boundingBox = rect,
            cornerPoints = emptyList(),
            recognizedLanguages = emptyList(),
            lines = listOf(OcrLineEvidence(text, rect, elements = elements))
        )
    }
}
