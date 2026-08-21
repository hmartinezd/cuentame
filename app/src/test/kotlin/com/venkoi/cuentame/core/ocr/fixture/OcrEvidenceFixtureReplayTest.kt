package com.venkoi.cuentame.core.ocr.fixture

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.ocr.parser.DeterministicPurchaseInvoiceParser
import org.junit.Test
import java.math.BigDecimal

class OcrEvidenceFixtureReplayTest {
    @Test
    fun `loads page evidence in declared order and replays parser`() {
        val pages = OcrEvidenceFixtureLoader.loadPages("synthetic-loader-contract")

        Truth.assertThat(pages).hasSize(2)
        assertThat(pages.map { it.text }).containsExactly("FIRST PAGE", "SECOND PAGE").inOrder()
        assertThat(pages[0].blocks.single().lines.single().elements.single().confidence).isEqualTo(0.97f)

        val parsed = DeterministicPurchaseInvoiceParser().parse(pages)
        assertThat(parsed).isNotNull()
    }

    @Test
    fun `golden mismatch reports expected parsed missing products totals and warnings`() {
        val actual = DeterministicPurchaseInvoiceParser().parse(
            OcrEvidenceFixtureLoader.loadPages("synthetic-loader-contract")
        )

        val failure = runCatching {
            assertGoldenInvoice(
                fixture = "diagnostic-contract",
                expected = ExpectedInvoice(
                    total = BigDecimal("99.99"),
                    lines = listOf(
                        ExpectedInvoiceLine(
                            vendorCode = "MISSING",
                            description = "Missing product"
                        )
                    )
                ),
                actual = actual
            )
        }.exceptionOrNull()

        Truth.assertThat(failure).isInstanceOf(AssertionError::class.java)
        Truth.assertThat(failure).hasMessageThat().contains("expected products:")
        Truth.assertThat(failure).hasMessageThat().contains("parsed products:")
        Truth.assertThat(failure).hasMessageThat().contains("missing products:")
        Truth.assertThat(failure).hasMessageThat().contains("final total:")
        Truth.assertThat(failure).hasMessageThat().contains("parser warnings:")
    }
}