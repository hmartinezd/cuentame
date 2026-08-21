package com.venkoi.restaurantops.core.domain.service

import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.model.purchase.InvoiceLineMatchStatus
import com.venkoi.restaurantops.core.model.purchase.PurchaseInvoiceLineMatch
import com.venkoi.restaurantops.core.ocr.parser.ParsedField
import com.venkoi.restaurantops.core.ocr.parser.ParsedInvoiceLineCandidate
import com.venkoi.restaurantops.core.ocr.parser.PurchaseInvoiceParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PurchaseInvoiceFingerprinterTest {

    private val fingerprinter = PurchaseInvoiceFingerprinter()

    private val receiptId = PurchaseReceiptId("r1")
    private val supplierId = "s1"
    private val sourceDocumentSha256 = "doc-sha"

    @Test
    fun `fingerprint is deterministic for identical inputs`() {
        val parseResult = createParseResult()
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches)
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches)

        assertEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when receipt ID changes`() {
        val parseResult = createParseResult()
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches)
        val f2 = fingerprinter.fingerprint(PurchaseReceiptId("r2"), supplierId, sourceDocumentSha256, parseResult, matches)

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when supplier ID changes`() {
        val parseResult = createParseResult()
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches)
        val f2 = fingerprinter.fingerprint(receiptId, "s2", sourceDocumentSha256, parseResult, matches)

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when document SHA changes`() {
        val parseResult = createParseResult()
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches)
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, "other-sha", parseResult, matches)

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when parse result ID changes`() {
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, createParseResult(id = "p1"), matches)
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, createParseResult(id = "p2"), matches)

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when header values change`() {
        val matches = listOf(createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, createParseResult(total = BigDecimal("100")), matches)
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, createParseResult(total = BigDecimal("200")), matches)

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when line match changes`() {
        val parseResult = createParseResult()

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, listOf(createMatch(0, ingredientId = "i1")))
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, listOf(createMatch(0, ingredientId = "i2")))

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint changes when match supplier changes`() {
        val parseResult = createParseResult()
        
        val match1 = createMatch(0, supplierId = "s1")
        val match2 = createMatch(0, supplierId = "s2")

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, listOf(match1))
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, listOf(match2))

        assertNotEquals(f1, f2)
    }

    @Test
    fun `fingerprint is invariant to match order`() {
        val parseResult = createParseResult(lines = listOf(createParsedLine(0), createParsedLine(1)))
        
        val matches1 = listOf(createMatch(0), createMatch(1))
        val matches2 = listOf(createMatch(1), createMatch(0))

        val f1 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches1)
        val f2 = fingerprinter.fingerprint(receiptId, supplierId, sourceDocumentSha256, parseResult, matches2)

        assertEquals(f1, f2)
    }

    private fun createParseResult(
        id: String = "p1",
        total: BigDecimal = BigDecimal("100"),
        lines: List<ParsedInvoiceLineCandidate> = listOf(createParsedLine(0))
    ) = PurchaseInvoiceParseResult(
        id = id,
        supplierNameCandidate = ParsedField("Sysco", "Sysco", 0.9f),
        invoiceNumber = ParsedField("INV1", "INV1", 0.9f),
        invoiceDate = ParsedField("2023-01-01", LocalDate.of(2023, 1, 1), 0.9f),
        currency = ParsedField("USD", "USD", 1.0f),
        subtotal = ParsedField("100", BigDecimal("100"), 0.9f),
        discount = ParsedField(null, null, null),
        fees = ParsedField(null, null, null),
        tax = ParsedField(null, null, null),
        total = ParsedField("100", total, 0.9f),
        lines = lines,
        confidence = 0.9f
    )

    private fun createParsedLine(index: Int) = ParsedInvoiceLineCandidate(
        index = index,
        vendorCode = ParsedField("V1", "V1", 0.9f),
        description = ParsedField("Item $index", "Item $index", 0.9f),
        quantity = ParsedField("1", BigDecimal.ONE, 0.9f),
        packageText = ParsedField("EA", "EA", 0.9f),
        unitPrice = ParsedField("100", BigDecimal("100"), 0.9f),
        lineTotal = ParsedField("100", BigDecimal("100"), 0.9f),
        confidence = 0.9f
    )

    private fun createMatch(lineIndex: Int, ingredientId: String = "i1", supplierId: String = "s1") = PurchaseInvoiceLineMatch(
        parseResultId = "p1",
        lineIndex = lineIndex,
        status = InvoiceLineMatchStatus.CONFIRMED,
        supplierId = SupplierId(supplierId),
        ingredientId = IngredientId(ingredientId),
        unitOptionId = IngredientUnitOptionId("u1"),
        inventoryAreaId = InventoryAreaId("a1"),
        mappingId = null,
        matchMethod = "manual",
        matchConfidence = 1.0f,
        confirmedAt = Instant.now()
    )
}
