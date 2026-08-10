package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.*
import com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class GenerateInvoiceProposalUseCaseTest {

    private val purchaseRepository = mockk<PurchaseRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val supplierRepository = mockk<SupplierRepository>()
    private val areaRepository = mockk<InventoryAreaRepository>()
    private val lineCalculator = PurchaseLineCalculator()
    
    private lateinit var useCase: GenerateInvoiceProposalUseCase

    private val receiptId = PurchaseReceiptId("r1")
    private val restaurantId = RestaurantId("rest1")
    private val ingredientId = IngredientId("i1")
    private val unitOptionId = IngredientUnitOptionId("u1")
    private val areaId = InventoryAreaId("a1")

    @Before
    fun setup() {
        useCase = GenerateInvoiceProposalUseCase(
            purchaseRepository,
            ingredientRepository,
            supplierRepository,
            areaRepository,
            lineCalculator
        )
    }

    @Test
    fun `successful proposal generation`() = runBlocking {
        // Arrange
        val receipt = PurchaseReceipt(
            id = receiptId,
            restaurantId = restaurantId,
            purchaseDate = Instant.now(),
            status = DocumentStatus.DRAFT,
            attachmentPath = "path/to/invoice.pdf",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val parseResult = PurchaseInvoiceParseResult(
            id = "p1",
            supplierNameCandidate = ParsedField("Sysco", "Sysco", 0.9f),
            invoiceNumber = ParsedField("INV123", "INV123", 0.9f),
            invoiceDate = ParsedField("2023-01-01", LocalDate.of(2023, 1, 1), 0.9f),
            currency = ParsedField("USD", "USD", 1.0f),
            subtotal = ParsedField("100", BigDecimal("100"), 0.9f),
            discount = ParsedField(null, null, null),
            fees = ParsedField(null, null, null),
            tax = ParsedField(null, null, null),
            total = ParsedField("100", BigDecimal("100"), 0.9f),
            lines = listOf(
                ParsedInvoiceLineCandidate(
                    index = 0,
                    vendorCode = ParsedField("V1", "V1", 0.9f),
                    description = ParsedField("Tomato", "Tomato", 0.9f),
                    quantity = ParsedField("2", BigDecimal("2"), 0.9f),
                    packageText = ParsedField("CS", "CS", 0.9f),
                    unitPrice = ParsedField("10", BigDecimal("10"), 0.9f),
                    lineTotal = ParsedField("20", BigDecimal("20"), 0.9f),
                    confidence = 0.9f
                )
            ),
            confidence = 0.9f
        )
        val ocrResult = PurchaseInvoiceOcrResult(
            id = "o1",
            purchaseReceiptId = receiptId,
            sourceDocumentSha256 = "sha256",
            sourceMimeType = "application/pdf",
            engine = "test",
            evidenceSchemaVersion = 1,
            pageCount = 1,
            fullText = "",
            processedAt = Instant.now()
        )
        val matches = listOf(
            PurchaseInvoiceLineMatch(
                parseResultId = "p1",
                lineIndex = 0,
                status = InvoiceLineMatchStatus.CONFIRMED,
                supplierId = SupplierId("s1"),
                ingredientId = ingredientId,
                unitOptionId = unitOptionId,
                inventoryAreaId = areaId,
                mappingId = null,
                matchMethod = "manual",
                matchConfidence = 1.0f,
                confirmedAt = Instant.now()
            )
        )

        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(ocrResult)
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(matches)
        
        coEvery { ingredientRepository.getById(ingredientId) } returns Ingredient(
            id = ingredientId, 
            restaurantId = restaurantId, 
            name = "Tomato", 
            normalizedName = "tomato", 
            categoryId = null, 
            baseUnitId = UnitId("u_base"), 
            defaultAreaId = areaId, 
            isActive = true, 
            createdAt = Instant.now(), 
            updatedAt = Instant.now()
        )
        coEvery { ingredientRepository.getUnitOption(unitOptionId) } returns IngredientUnitOption(unitOptionId, ingredientId, "Case", "CS", null, BigDecimal("1"), false, false, false, true, Instant.now(), Instant.now())
        coEvery { areaRepository.getById(areaId) } returns InventoryArea(areaId, restaurantId, "Walk-In", "walk-in", 0, true, Instant.now(), Instant.now())

        // Act
        val proposal = useCase.execute(receiptId)

        // Assert
        assert(proposal != null)
        assertEquals(receiptId, proposal!!.purchaseReceiptId)
        assertEquals("p1", proposal.parseResultId)
        assertEquals(1, proposal.lines.size)
        assertEquals(BigDecimal("2"), proposal.lines[0].quantityEntered)
        assertEquals(BigDecimal("20"), proposal.lines[0].lineTotal)
        assertTrue(proposal.blockingIssues.isEmpty())
    }

    @Test
    fun `blocks when lines are unresolved`() = runBlocking {
        // Arrange
        val receipt = PurchaseReceipt(
            id = receiptId,
            restaurantId = restaurantId,
            purchaseDate = Instant.now(),
            status = DocumentStatus.DRAFT,
            attachmentPath = "path",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val parseResult = PurchaseInvoiceParseResult(
            id = "p1",
            supplierNameCandidate = ParsedField(null, null, null),
            invoiceNumber = ParsedField(null, null, null),
            invoiceDate = ParsedField(null, null, null),
            currency = ParsedField(null, null, null),
            subtotal = ParsedField(null, null, null),
            discount = ParsedField(null, null, null),
            fees = ParsedField(null, null, null),
            tax = ParsedField(null, null, null),
            total = ParsedField(null, null, null),
            lines = listOf(
                ParsedInvoiceLineCandidate(0, ParsedField(null, null, null), ParsedField("Tomato", null, null), ParsedField(null, null, null), ParsedField(null, null, null), ParsedField(null, null, null), ParsedField(null, null, null), null)
            ),
            confidence = null
        )
        val ocrResult = mockk<PurchaseInvoiceOcrResult> {
            every { sourceDocumentSha256 } returns "sha"
        }

        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(ocrResult)
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())

        // Act
        val proposal = useCase.execute(receiptId)

        // Assert
        assertTrue(proposal!!.blockingIssues.contains(MaterializationBlockingIssue.UnresolvedLines))
    }
}
