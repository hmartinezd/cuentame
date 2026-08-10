package com.miara.cuentame.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.SourceMutationResult
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.ParsedField
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
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
class RoomPurchaseInvoiceCorrectnessTest {

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
    fun materialization_isAllOrNothing_failsIfAnyLineBlocked() = runBlocking {
        val receiptId = seedPurchaseWithParseResult(lineCount = 2)
        
        // 1. Manually break one match (set status to SUGGESTED)
        val matches = repository.observeLineMatchesForReceipt(receiptId).first()
        val brokenMatches = matches.mapIndexed { index, match ->
            if (index == 1) match.copy(status = InvoiceLineMatchStatus.SUGGESTED) else match
        }
        val parseId = database.purchaseParseDao().getParseResultIdForReceipt(receiptId.value)!!
        repository.saveLineMatchesForReceipt(receiptId, parseId, brokenMatches)

        // 2. Generate Proposal
        val proposal = generateProposalUseCase.execute(receiptId)!!
        assertThat(proposal.lines[1].blockingReason).isNotNull()
        assertThat(proposal.blockingIssues).isNotEmpty()

        // 3. Attempt Apply -> Should fail
        val result = applyInvoiceUseCase.execute(proposal)
        assertThat(result).isInstanceOf(PurchaseInvoiceMaterializationResult.Failure::class.java)
        
        // 4. Verify ZERO PurchaseLines were created (Atomicity proof)
        val lines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(lines).isEmpty()
    }

    @Test
    fun materialization_detectsManualEditConflict_atomic() = runBlocking {
        val receiptId = seedPurchaseWithParseResult(lineCount = 2)
        
        // 1. First successful materialization
        val proposal = generateProposalUseCase.execute(receiptId)!!
        val result1 = applyInvoiceUseCase.execute(proposal)
        assertThat(result1).isEqualTo(PurchaseInvoiceMaterializationResult.Success)
        
        val initialLines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(initialLines).hasSize(2)

        // 2. Manually edit the second line in the draft
        val lineToEdit = initialLines[1]
        database.purchaseDao().updateLine(lineToEdit.copy(lineTotal = "999.99"))

        // 3. Attempt to materialize again -> Should fail due to ManualEditConflict
        // We use the same proposal (it should still be valid/fresh unless we change the source)
        val result2 = applyInvoiceUseCase.execute(proposal)
        assertThat(result2).isInstanceOf(PurchaseInvoiceMaterializationResult.Failure::class.java)
        val failure = (result2 as PurchaseInvoiceMaterializationResult.Failure).reason
        assertThat(failure).isEqualTo(PurchaseInvoiceMaterializationFailure.ManualEditConflict)

        // 4. Verify the first line was NOT updated (Phase B was never reached)
        val finalLines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        
        // We check that the second line REMAINS manually edited.
        val line2 = finalLines.find { it.id == initialLines[1].id }!!
        assertThat(line2.lineTotal).isEqualTo("999.99")
    }

    @Test
    fun sourceLocking_preventsMutationAfterMaterialization() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        val proposal = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposal)

        // 1. Attempt to replace document -> SourceLocked
        val resultAttach = repository.attachDocument(receiptId, "new-loc", "new-name")
        assertThat(resultAttach).isEqualTo(SourceMutationResult.SourceLocked)

        // 2. Attempt to delete OCR -> SourceLocked
        val resultDeleteOcr = repository.deleteOcrResult(receiptId)
        assertThat(resultDeleteOcr).isEqualTo(SourceMutationResult.SourceLocked)

        // 3. Attempt to delete Parse -> SourceLocked
        val resultDeleteParse = repository.deleteParseResult(receiptId)
        assertThat(resultDeleteParse).isEqualTo(SourceMutationResult.SourceLocked)

        // 4. Attempt to update line -> SourceLocked
        val resultUpdateLine = repository.updateParsedLine(receiptId, 0, true, null)
        assertThat(resultUpdateLine).isEqualTo(SourceMutationResult.SourceLocked)
    }

    @Test
    fun manualEditDetection_isNumericEquivalentAware() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        val proposal = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposal)
        
        val initialLines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        val line = initialLines[0]
        
        // 1. Edit with equivalent numeric value (e.g. "1" -> "1.00")
        database.purchaseDao().updateLine(line.copy(quantityEntered = "1.00"))
        
        // 2. Attempt to materialize again -> Should NOT fail
        val result = applyInvoiceUseCase.execute(proposal)
        assertThat(result).isEqualTo(PurchaseInvoiceMaterializationResult.Success)
    }

    private suspend fun seedPurchaseWithParseResult(lineCount: Int = 1): PurchaseReceiptId {
        val supplierId = SupplierId(TestSeeder.SUPPLIER_ID)
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, supplierId, "INV-1", Instant.now(), null))
        val sha = "sha256-" + receiptId.value
        
        repository.saveOcrResult(
            result = PurchaseInvoiceOcrResult(
                id = "ocr-" + receiptId.value,
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
            expectedAttachmentPath = "",
            expectedDocumentSha256 = sha
        )

        val lines = (0 until lineCount).map { i ->
            ParsedInvoiceLineCandidate(
                index = i,
                vendorCode = ParsedField("V$i", "V$i", 0.9f),
                description = ParsedField("Item $i", "Item $i", 0.9f),
                quantity = ParsedField("1", BigDecimal.ONE, 0.9f),
                packageText = ParsedField("EA", "EA", 0.9f),
                unitPrice = ParsedField("10", BigDecimal.TEN, 0.9f),
                lineTotal = ParsedField("10", BigDecimal.TEN, 0.9f),
                confidence = 0.9f
            )
        }

        val parseResult = PurchaseInvoiceParseResult(
            id = "parse-" + receiptId.value,
            supplierNameCandidate = ParsedField("Sysco", "Sysco", 0.9f),
            invoiceNumber = ParsedField("INV-1", "INV-1", 0.9f),
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
        repository.saveParseResult(receiptId, "ocr-" + receiptId.value, sha, parseResult)

        val matches = (0 until lineCount).map { i ->
            PurchaseInvoiceLineMatch(
                parseResultId = parseResult.id,
                lineIndex = i,
                status = InvoiceLineMatchStatus.CONFIRMED,
                supplierId = supplierId,
                ingredientId = IngredientId(TestSeeder.ING_ID),
                unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
                inventoryAreaId = InventoryAreaId(TestSeeder.AREA_ID),
                mappingId = null,
                matchMethod = "manual",
                matchConfidence = 1.0f,
                confirmedAt = Instant.now()
            )
        }
        repository.saveLineMatchesForReceipt(receiptId, parseResult.id, matches)
        
        return receiptId
    }
}
