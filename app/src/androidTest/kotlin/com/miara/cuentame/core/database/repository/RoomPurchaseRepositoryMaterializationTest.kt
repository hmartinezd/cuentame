package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.*
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomPurchaseRepositoryMaterializationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomPurchaseRepository

    @Inject
    lateinit var generateProposalUseCase: GenerateInvoiceProposalUseCase

    @Inject
    lateinit var applyInvoiceUseCase: ApplyInvoiceToPurchaseDraftUseCase

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
        }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun materializeInvoice_createsPurchaseLines_idempotent() = runBlocking {
        // 1. Arrange: Create Purchase with Document and Parse Result
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null))
        val sha = "abc123sha"
        
        repository.saveOcrResult(
            result = PurchaseInvoiceOcrResult(
                id = "ocr1",
                purchaseReceiptId = receiptId,
                sourceDocumentSha256 = sha,
                sourceMimeType = "application/pdf",
                engine = "test",
                evidenceSchemaVersion = 1,
                pageCount = 1,
                fullText = "OCR Text",
                processedAt = Instant.now()
            ),
            pages = emptyList(),
            expectedAttachmentPath = "", // Placeholder
            expectedDocumentSha256 = sha
        )

        val parseResult = PurchaseInvoiceParseResult(
            id = "parse1",
            supplierNameCandidate = ParsedField("Sysco", "Sysco", 0.9f),
            invoiceNumber = ParsedField("INV-1", "INV-1", 0.9f),
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
                    unitPrice = ParsedField("50", BigDecimal("50"), 0.9f),
                    lineTotal = ParsedField("100", BigDecimal("100"), 0.9f),
                    confidence = 0.9f
                )
            ),
            confidence = 0.9f
        )
        repository.saveParseResult(receiptId, "ocr1", sha, parseResult)

        val matches = listOf(
            PurchaseInvoiceLineMatch(
                parseResultId = "parse1",
                lineIndex = 0,
                status = InvoiceLineMatchStatus.CONFIRMED,
                supplierId = null,
                ingredientId = IngredientId(TestSeeder.ING_ID),
                unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
                inventoryAreaId = InventoryAreaId(TestSeeder.AREA_ID),
                mappingId = null,
                matchMethod = "manual",
                matchConfidence = 1.0f,
                confirmedAt = Instant.now()
            )
        )
        repository.saveLineMatchesForReceipt(receiptId, "parse1", matches)

        // 2. Act: Generate Proposal and Apply
        val proposal = generateProposalUseCase.execute(receiptId)
        assertThat(proposal).isNotNull()
        assertThat(proposal!!.blockingIssues).isEmpty()

        val result = applyInvoiceUseCase.execute(proposal)
        assertThat(result).isEqualTo(PurchaseInvoiceMaterializationResult.Success)

        // 3. Assert: Verify PurchaseLines
        val lines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(lines).hasSize(1)
        assertThat(lines[0].quantityEntered).isEqualTo("2")
        assertThat(lines[0].lineTotal).isEqualTo("100")
        
        // 4. Assert: Verify Status remains DRAFT and no Movements
        val receipt = repository.getReceipt(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.DRAFT)
        val movements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(movements).isEmpty()

        // 5. Act: Apply Again (Idempotency)
        val result2 = applyInvoiceUseCase.execute(proposal)
        assertThat(result2).isEqualTo(PurchaseInvoiceMaterializationResult.Success)
        
        val lines2 = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(lines2).hasSize(1) // Still 1
    }
}
