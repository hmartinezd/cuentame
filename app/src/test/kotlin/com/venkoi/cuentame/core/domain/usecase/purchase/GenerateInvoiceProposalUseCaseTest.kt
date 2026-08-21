package com.venkoi.cuentame.core.domain.usecase.purchase

import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.domain.service.PurchaseInvoiceFingerprinter
import com.venkoi.cuentame.core.domain.service.PurchaseLineCalculator
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryArea
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import com.venkoi.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.venkoi.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.venkoi.cuentame.core.model.purchase.PurchaseReceipt
import com.venkoi.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.venkoi.cuentame.core.ocr.parser.*
import com.venkoi.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val unitRepository = mockk<UnitRepository>()
    private val lineCalculator = PurchaseLineCalculator()
    private val fingerprinter = mockk<PurchaseInvoiceFingerprinter>()
    
    private lateinit var useCase: GenerateInvoiceProposalUseCase

    private val receiptId = PurchaseReceiptId("r1")
    private val restaurantId = RestaurantId("rest1")
    private val ingredientId = IngredientId("i1")
    private val unitOptionId = IngredientUnitOptionId("u1")
    private val areaId = InventoryAreaId("a1")
    private val baseUnitId = UnitId("u_base")

    @Before
    fun setup() {
        useCase = GenerateInvoiceProposalUseCase(
            purchaseRepository,
            ingredientRepository,
            supplierRepository,
            areaRepository,
            unitRepository,
            lineCalculator,
            fingerprinter
        )
        
        every { fingerprinter.fingerprint(any(), any(), any(), any(), any()) } returns "test-fingerprint"
    }

    @Test
    fun `successful proposal generation`() = runBlocking {
        // Arrange
        val receipt = createReceipt(DocumentStatus.DRAFT, supplierId = SupplierId("s1"))
        val parseResult = createParseResult()
        val ocrResult = createOcrResult()
        val matches = listOf(createMatch())

        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(ocrResult)
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(matches)
        
        coEvery { supplierRepository.getSupplier(SupplierId("s1")) } returns mockk {
            every { id } returns SupplierId("s1")
            every { name } returns "Sysco"
        }
        coEvery { ingredientRepository.getById(ingredientId) } returns createIngredient()
        coEvery { ingredientRepository.getUnitOption(unitOptionId) } returns createUnitOption()
        coEvery { areaRepository.getById(areaId) } returns createArea()
        coEvery { unitRepository.getById(baseUnitId) } returns UnitOfMeasure(baseUnitId, "Each", "ea", com.venkoi.cuentame.core.model.inventory.UnitDimension.COUNT, BigDecimal.ONE, true, 0)

        // Act
        val proposal = useCase.execute(receiptId)

        // Assert
        assert(proposal != null)
        assertEquals(receiptId, proposal!!.purchaseReceiptId)
        assertEquals("p1", proposal.parseResultId)
        assertEquals("test-fingerprint", proposal.sourceStateFingerprint)
        assertEquals(1, proposal.lines.size)
        assertEquals(BigDecimal("2"), proposal.lines[0].quantityEntered)
        assertEquals(BigDecimal("20"), proposal.lines[0].lineTotal)
        assertTrue(proposal.blockingIssues.isEmpty())
    }

    @Test
    fun `blocks when status is not DRAFT`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.POSTED)
        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(createParseResult())
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(createOcrResult())
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())

        val proposal = useCase.execute(receiptId)

        assertTrue(proposal!!.blockingIssues.contains(MaterializationBlockingIssue.PurchaseAlreadyPosted))
    }

    @Test
    fun `blocks when attachment is missing`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.DRAFT, attachmentPath = null)
        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(createParseResult())
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(createOcrResult())
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())

        val proposal = useCase.execute(receiptId)

        assertTrue(proposal!!.blockingIssues.contains(MaterializationBlockingIssue.DocumentChanged))
    }

    @Test
    fun `blocks when supplier is missing`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.DRAFT, supplierId = null)
        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(createParseResult())
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(createOcrResult())
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())

        val proposal = useCase.execute(receiptId)

        assertTrue(proposal!!.blockingIssues.contains(MaterializationBlockingIssue.MissingSupplier))
    }

    @Test
    fun `blocks when lines are unresolved`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.DRAFT)
        val parseResult = createParseResult(lines = listOf(createParsedLine(index = 0)))
        
        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(createOcrResult())
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList()) // No matches

        val proposal = useCase.execute(receiptId)

        assertTrue(proposal!!.blockingIssues.contains(MaterializationBlockingIssue.UnresolvedLines))
        assertEquals(MaterializationBlockingIssue.UnresolvedMatch, proposal.lines[0].blockingReason)
    }

    @Test
    fun `no silent line drops - all active lines are included`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.DRAFT)
        val parseResult = createParseResult(lines = listOf(
            createParsedLine(index = 0, isIgnored = false),
            createParsedLine(index = 1, isIgnored = true),
            createParsedLine(index = 2, isIgnored = false)
        ))

        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(createOcrResult())
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())

        val proposal = useCase.execute(receiptId)

        assertEquals(2, proposal!!.lines.size)
        assertEquals(0, proposal.lines[0].lineIndex)
        assertEquals(2, proposal.lines[1].lineIndex)
    }

    @Test
    fun `fingerprinter determinism is reflected in proposal`() = runBlocking {
        val receipt = createReceipt(DocumentStatus.DRAFT)
        val parseResult = createParseResult()
        val ocrResult = createOcrResult()
        val matches = emptyList<PurchaseInvoiceLineMatch>()

        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(parseResult)
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(ocrResult)
        every { purchaseRepository.observeLineMatchesForReceipt(receiptId) } returns flowOf(matches)

        every { fingerprinter.fingerprint(receiptId, any(), "sha256", parseResult, matches) } returns "specific-hash"

        val proposal = useCase.execute(receiptId)

        assertEquals("specific-hash", proposal!!.sourceStateFingerprint)
    }

    private fun createReceipt(
        status: DocumentStatus, 
        supplierId: SupplierId? = null,
        attachmentPath: String? = "path"
    ) = PurchaseReceipt(
        id = receiptId,
        restaurantId = restaurantId,
        supplierId = supplierId,
        purchaseDate = Instant.now(),
        status = status,
        attachmentPath = attachmentPath,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createParseResult(lines: List<ParsedInvoiceLineCandidate> = listOf(createParsedLine())) = PurchaseInvoiceParseResult(
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
        lines = lines,
        confidence = 0.9f
    )

    private fun createParsedLine(index: Int = 0, isIgnored: Boolean = false) = ParsedInvoiceLineCandidate(
        index = index,
        vendorCode = ParsedField("V1", "V1", 0.9f),
        description = ParsedField("Tomato", "Tomato", 0.9f),
        quantity = ParsedField("2", BigDecimal("2"), 0.9f),
        packageText = ParsedField("CS", "CS", 0.9f),
        unitPrice = ParsedField("10", BigDecimal("10"), 0.9f),
        lineTotal = ParsedField("20", BigDecimal("20"), 0.9f),
        isIgnored = isIgnored,
        confidence = 0.9f
    )

    private fun createOcrResult() = PurchaseInvoiceOcrResult(
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

    private fun createMatch() = PurchaseInvoiceLineMatch(
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

    private fun createIngredient() = Ingredient(
        id = ingredientId, 
        restaurantId = restaurantId, 
        name = "Tomato", 
        normalizedName = "tomato", 
        categoryId = null, 
        baseUnitId = baseUnitId, 
        defaultAreaId = areaId, 
        isActive = true, 
        createdAt = Instant.now(), 
        updatedAt = Instant.now()
    )

    private fun createUnitOption() = IngredientUnitOption(unitOptionId, ingredientId, "Case", "CS", null, BigDecimal("1"), false, false, false, true, Instant.now(), Instant.now())
    private fun createArea() = InventoryArea(areaId, restaurantId, "Walk-In", "walk-in", 0, true, Instant.now(), Instant.now())
}
