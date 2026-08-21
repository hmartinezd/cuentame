package com.venkoi.cuentame.core.ocr.fixture

import com.venkoi.cuentame.core.ocr.parser.InvoiceParseWarning
import com.venkoi.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.venkoi.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.fail

data class ExpectedInvoice(
    val supplier: String? = null,
    val invoiceNumber: String? = null,
    val date: LocalDate? = null,
    val subtotal: BigDecimal? = null,
    val discount: BigDecimal? = null,
    val fees: BigDecimal? = null,
    val tax: BigDecimal? = null,
    val total: BigDecimal? = null,
    val currency: String? = null,
    val warnings: Set<InvoiceParseWarning>? = null,
    val lines: List<ExpectedInvoiceLine> = emptyList()
)

data class ExpectedInvoiceLine(
    val vendorCode: String? = null,
    val description: String,
    val quantity: BigDecimal? = null,
    val packageText: String? = null,
    val unitPrice: BigDecimal? = null,
    val lineTotal: BigDecimal? = null
)

fun assertGoldenInvoice(fixture: String, expected: ExpectedInvoice, actual: PurchaseInvoiceParseResult) {
    val mismatches = mutableListOf<String>()
    compare("supplier", expected.supplier, actual.supplierNameCandidate.normalizedValue, mismatches)
    compare("invoice number", expected.invoiceNumber, actual.invoiceNumber.normalizedValue, mismatches)
    compare("date", expected.date, actual.invoiceDate.normalizedValue, mismatches)
    compareDecimal("subtotal", expected.subtotal, actual.subtotal.normalizedValue, mismatches)
    compareDecimal("discount", expected.discount, actual.discount.normalizedValue, mismatches)
    compareDecimal("fees", expected.fees, actual.fees.normalizedValue, mismatches)
    compareDecimal("tax", expected.tax, actual.tax.normalizedValue, mismatches)
    compareDecimal("final total", expected.total, actual.total.normalizedValue, mismatches)
    compare("currency", expected.currency, actual.currency.normalizedValue, mismatches)
    expected.warnings?.let { compare("warnings", it, actual.warnings.toSet(), mismatches) }

    val unmatchedActual = actual.lines.toMutableList()
    val missing = mutableListOf<ExpectedInvoiceLine>()
    expected.lines.forEach { expectedLine ->
        val match = unmatchedActual.firstOrNull { lineMatchesIdentity(expectedLine, it) }
        if (match == null) missing += expectedLine else {
            unmatchedActual.remove(match)
            compareLine(expectedLine, match, mismatches)
        }
    }
    if (missing.isNotEmpty()) mismatches += "missing products: ${missing.joinToString { it.identity() }}"
    if (unmatchedActual.isNotEmpty()) mismatches += "unexpected products: ${unmatchedActual.joinToString { it.identity() }}"

    if (mismatches.isNotEmpty()) {
        fail(buildString {
            appendLine("OCR replay mismatch for fixture '$fixture'")
            appendLine("expected products: ${expected.lines.joinToString { it.identity() }}")
            appendLine("parsed products: ${actual.lines.joinToString { it.identity() }}")
            mismatches.forEach { appendLine("- $it") }
            append("parser warnings: ${actual.warnings}")
        })
    }
}

private fun lineMatchesIdentity(expected: ExpectedInvoiceLine, actual: ParsedInvoiceLineCandidate): Boolean =
    expected.vendorCode?.let { it == actual.vendorCode.normalizedValue }
        ?: (expected.description == actual.description.normalizedValue)

private fun compareLine(expected: ExpectedInvoiceLine, actual: ParsedInvoiceLineCandidate, out: MutableList<String>) {
    val prefix = "product ${expected.identity()}"
    compare("$prefix vendor code", expected.vendorCode, actual.vendorCode.normalizedValue, out)
    compare("$prefix description", expected.description, actual.description.normalizedValue, out)
    compareDecimal("$prefix quantity", expected.quantity, actual.quantity.normalizedValue, out)
    compare("$prefix package", expected.packageText, actual.packageText.normalizedValue, out)
    compareDecimal("$prefix unit price", expected.unitPrice, actual.unitPrice.normalizedValue, out)
    compareDecimal("$prefix line total", expected.lineTotal, actual.lineTotal.normalizedValue, out)
}

private fun compare(label: String, expected: Any?, actual: Any?, out: MutableList<String>) {
    if (expected != null && expected != actual) out += "$label: expected <$expected>, parsed <$actual>"
}

private fun compareDecimal(label: String, expected: BigDecimal?, actual: BigDecimal?, out: MutableList<String>) {
    if (expected != null && (actual == null || expected.compareTo(actual) != 0)) {
        out += "$label: expected <$expected>, parsed <$actual>"
    }
}

private fun ExpectedInvoiceLine.identity() = vendorCode ?: description
private fun ParsedInvoiceLineCandidate.identity() = vendorCode.normalizedValue ?: description.normalizedValue ?: "line $index"
