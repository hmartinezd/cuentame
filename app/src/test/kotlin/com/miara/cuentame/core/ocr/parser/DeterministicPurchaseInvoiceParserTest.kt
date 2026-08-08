package com.miara.cuentame.core.ocr.parser

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.ocr.api.*
import org.junit.Test
import java.math.BigDecimal

class DeterministicPurchaseInvoiceParserTest {

    private val parser = DeterministicPurchaseInvoiceParser()

    @Test
    fun parseSimpleUsInvoice() {
        val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "ACME FOODS\nINV-123\n08/07/2026\nTOTAL 100.00",
                blocks = listOf(
                    OcrBlockEvidence(
                        text = "ACME FOODS",
                        boundingBox = OcrRect(100, 100, 300, 150),
                        cornerPoints = emptyList(),
                        recognizedLanguages = emptyList(),
                        lines = listOf(
                            OcrLineEvidence(
                                text = "ACME FOODS",
                                boundingBox = OcrRect(100, 100, 300, 150),
                                elements = listOf(
                                    OcrElementEvidence("ACME", OcrRect(100, 100, 200, 150)),
                                    OcrElementEvidence("FOODS", OcrRect(210, 100, 300, 150))
                                )
                            )
                        )
                    ),
                    OcrBlockEvidence(
                        text = "TOTAL 100.00",
                        boundingBox = OcrRect(100, 1000, 300, 1050),
                        cornerPoints = emptyList(),
                        recognizedLanguages = emptyList(),
                        lines = listOf(
                            OcrLineEvidence(
                                text = "TOTAL 100.00",
                                boundingBox = OcrRect(100, 1000, 300, 1050),
                                elements = listOf(
                                    OcrElementEvidence("TOTAL", OcrRect(100, 1000, 150, 1050)),
                                    OcrElementEvidence("100.00", OcrRect(200, 1000, 300, 1050))
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = parser.parse(pages)

        assertThat(result.supplierNameCandidate.normalizedValue).isEqualTo("ACME FOODS")
        assertThat(result.total.normalizedValue).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun handlesDecimalComma() {
         val pages = listOf(
            OcrPageEvidence(
                widthPx = 1000,
                heightPx = 2000,
                text = "TOTAL 1.234,56",
                blocks = listOf(
                    OcrBlockEvidence(
                        text = "TOTAL 1.234,56",
                        boundingBox = OcrRect(100, 1000, 300, 1050),
                        cornerPoints = emptyList(),
                        recognizedLanguages = emptyList(),
                        lines = listOf(
                            OcrLineEvidence(
                                text = "TOTAL 1.234,56",
                                boundingBox = OcrRect(100, 1000, 300, 1050),
                                elements = listOf(
                                    OcrElementEvidence("TOTAL", OcrRect(100, 1000, 150, 1050)),
                                    OcrElementEvidence("1.234,56", OcrRect(200, 1000, 300, 1050))
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = parser.parse(pages)
        assertThat(result.total.normalizedValue).isEqualTo(BigDecimal("1234.56"))
    }
}
