package com.miara.cuentame.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.SourceMutationResult
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.ParsedField
import com.miara.cuentame.core.ocr.parser.Correction
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.effectiveValue
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    @Inject
    lateinit var failureBoundary: com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary

    @Inject
    lateinit var documentStore: PurchaseDocumentStore

    @Inject
    @dagger.hilt.android.qualifiers.ApplicationContext
    lateinit var context: android.content.Context

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
        failureBoundary.reset()
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun materialization_isAllOrNothing_failsIfAnyLineBlocked() = runBlocking {
        val receiptId = seedPurchaseWithParseResult(lineCount = 2)
        
        // 1. Manually break one match (set status to SUGGESTED)
        val matches = repository.observeLineMatchesForReceipt(receiptId).first()
        val brokenMatches = matches.mapIndexed { index, match ->
            if (index == 1) match.copy(status = InvoiceLineMatchStatus.SUGGESTED, confirmedAt = null) else match
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
    fun materialization_propagatesCancellationInsteadOfPersistenceFailure() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        val proposal = generateProposalUseCase.execute(receiptId)!!
        failureBoundary.triggerOn(
            com.miara.cuentame.core.database.repository.IntegrationFailurePoints.PURCHASE_MATERIALIZATION_AFTER_START,
            CancellationException("test cancellation inside materialization")
        )

        val thrown = runCatching {
            repository.applyInvoiceToDraft(proposal)
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(failureBoundary.triggerCount).isEqualTo(1)
        assertThat(database.purchaseDao().getLinesForReceipt(receiptId.value)).isEmpty()
        assertThat(database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)).isNull()
        assertThat(database.purchaseParseDao().getParseResultForReceipt(receiptId.value)).isNotNull()
        assertThat(repository.observeLineMatchesForReceipt(receiptId).first()).hasSize(1)
        assertThat(repository.getReceipt(receiptId)?.invoiceNumber).isEqualTo("INV-1")
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

        // 2. Attempt to remove document -> SourceLocked
        val resultRemove = repository.removeDocument(receiptId)
        assertThat(resultRemove).isEqualTo(SourceMutationResult.SourceLocked)

        // 3. Attempt to save OCR -> SourceLocked
        val resultSaveOcr = repository.saveOcrResult(
            result = PurchaseInvoiceOcrResult("ocr-2", receiptId, "sha2", "pdf", "e", 1, 1, "", Instant.now()),
            pages = emptyList(),
            expectedAttachmentPath = "loc",
            expectedDocumentSha256 = "sha"
        )
        assertThat(resultSaveOcr).isEqualTo(SourceMutationResult.SourceLocked)

        // 4. Attempt to delete OCR -> SourceLocked
        val resultDeleteOcr = repository.deleteOcrResult(receiptId)
        assertThat(resultDeleteOcr).isEqualTo(SourceMutationResult.SourceLocked)

        // 5. Attempt to save parse result -> SourceLocked
        val resultSaveParse = repository.saveParseResult(
            receiptId = receiptId,
            ocrResultId = "ocr-1",
            sourceDocumentSha256 = "sha",
            result = PurchaseInvoiceParseResult(
                id = "p-2",
                supplierNameCandidate = ParsedField(null, null, null),
                invoiceNumber = ParsedField(null, null, null),
                invoiceDate = ParsedField(null, null, null),
                currency = ParsedField(null, null, null),
                subtotal = ParsedField(null, null, null),
                discount = ParsedField(null, null, null),
                fees = ParsedField(null, null, null),
                tax = ParsedField(null, null, null),
                total = ParsedField(null, null, null),
                lines = emptyList(),
                confidence = 1f
            )
        )
        assertThat(resultSaveParse).isEqualTo(SourceMutationResult.SourceLocked)

        // 6. Attempt to delete Parse -> SourceLocked
        val resultDeleteParse = repository.deleteParseResult(receiptId)
        assertThat(resultDeleteParse).isEqualTo(SourceMutationResult.SourceLocked)

        // 7. Attempt to update parsed line -> SourceLocked
        val resultUpdateLine = repository.updateParsedLine(receiptId, 0, true, null)
        assertThat(resultUpdateLine).isEqualTo(SourceMutationResult.SourceLocked)

        // 8. Attempt to update parse result corrections -> SourceLocked
        val resultUpdateParse = repository.updateParseResult(receiptId, com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections())
        assertThat(resultUpdateParse).isEqualTo(SourceMutationResult.SourceLocked)

        // 9. Attempt to save line matches -> SourceLocked
        val resultSaveMatches = repository.saveLineMatchesForReceipt(receiptId, proposal.parseResultId, emptyList())
        assertThat(resultSaveMatches).isEqualTo(SourceMutationResult.SourceLocked)

        // 10. Attempt to save single match -> SourceLocked
        val resultSaveMatch = repository.saveLineMatchForReceipt(receiptId, proposal.parseResultId, 
            PurchaseInvoiceLineMatch(proposal.parseResultId, 0, InvoiceLineMatchStatus.SUGGESTED, null, null, null, null, null, "", 0f, null)
        )
        assertThat(resultSaveMatch).isEqualTo(SourceMutationResult.SourceLocked)
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

    @Test
    fun materialization_rejectsMalformedProposals_noMutation() = runBlocking {
        val receiptId = seedPurchaseWithParseResult(lineCount = 1)
        val baseProposal = generateProposalUseCase.execute(receiptId)!!

        val malformedProposals = mutableListOf(
            // 1. Missing line
            baseProposal.copy(lines = emptyList()),
            // 2. Duplicate line index
            baseProposal.copy(lines = listOf(baseProposal.lines[0], baseProposal.lines[0])),
            // 3. Null ID on ready line
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(ingredientId = null))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(unitOptionId = null))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(areaId = null))),
            // 4. Null numeric fields
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(quantityEntered = null))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(quantityBase = null))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(factorToBase = null))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(lineTotal = null))),
            // 5. Mismatched supplier
            baseProposal.copy(supplierProposal = baseProposal.supplierProposal!!.copy(id = SupplierId("other-s"))),
            // 6. Tampered numeric values vs source
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(quantityEntered = BigDecimal("999")))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(lineTotal = BigDecimal("999")))),
            // 7. Mismatched relational data vs confirmed match
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(ingredientId = IngredientId("other-i")))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(unitOptionId = IngredientUnitOptionId("other-o")))),
            baseProposal.copy(lines = listOf(baseProposal.lines[0].copy(areaId = InventoryAreaId("other-a")))),
            // 8. Tampered headers
            baseProposal.copy(invoiceNumber = "TAMPERED"),
            baseProposal.copy(invoiceDate = LocalDate.now().plusDays(10))
        )

        for (proposal in malformedProposals) {
            val result = repository.applyInvoiceToDraft(proposal)
            assertThat(result).isInstanceOf(PurchaseInvoiceMaterializationResult.Failure::class.java)
            
            // Verify ZERO mutation (assuming parse/receipt were not deleted by some bug)
            assertThat(database.purchaseDao().getLinesForReceipt(receiptId.value)).isEmpty()
            assertThat(database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)).isNull()
        }
    }

    @Test
    fun confirmMatch_respectsSourceLocking() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        val proposal = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposal)

        // Attempt to confirm match after materialization
        try {
            repository.confirmInvoiceLineMatch(
                receiptId = receiptId,
                expectedParseResultId = proposal.parseResultId,
                expectedSupplierId = proposal.supplierProposal?.id,
                lineIndex = 0,
                ingredientId = IngredientId(TestSeeder.ING_ID),
                unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
                inventoryAreaId = InventoryAreaId(TestSeeder.AREA_ID),
                forceLearnMapping = false
            )
            assertThat(false).isTrue() // Should not reach here
        } catch (e: ValidationError.InvoiceSourceLocked) {
            // Expected
        }
    }

    @Test
    fun observeParseResult_reactsToManualLineAndEveryReviewMutation() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        val emissions = Channel<PurchaseInvoiceParseResult>(Channel.UNLIMITED)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeParseResult(receiptId).filterNotNull().collect(emissions::send)
        }
        suspend fun awaitEmission(): PurchaseInvoiceParseResult = withTimeout(5_000) { emissions.receive() }
        assertThat(awaitEmission().lines).hasSize(1)

        val manualCorrection = ParsedInvoiceLineCorrection(
            vendorCode = Correction("00042"),
            description = Correction("Reviewer item"),
            quantity = Correction(BigDecimal("3")),
            packageText = Correction("6 x 1 lb"),
            unitPrice = Correction(BigDecimal("4.25")),
            lineTotal = Correction(BigDecimal("12.75"))
        )
        assertThat(repository.addManualParsedLine(receiptId, manualCorrection))
            .isEqualTo(SourceMutationResult.Success)
        val withManualLine = awaitEmission()
        assertThat(withManualLine.lines).hasSize(2)
        val manualLine = withManualLine.lines.single { it.index == 1 }
        assertThat(manualLine.confidence).isNull()
        assertThat(manualLine.evidenceRefs).isEmpty()
        assertThat(manualLine.description.normalizedValue).isNull()
        assertThat(manualLine.correction).isEqualTo(manualCorrection)

        val identityCorrection = ParsedInvoiceLineCorrection(
            vendorCode = Correction("00099"),
            description = Correction("Corrected item"),
            packageText = Correction("12 pack")
        )
        repository.updateParsedLine(receiptId, 0, false, identityCorrection)
        val identityEdit = awaitEmission().lines.single { it.index == 0 }
        assertThat(identityEdit.vendorCode.effectiveValue(identityEdit.correction?.vendorCode)).isEqualTo("00099")
        assertThat(identityEdit.description.effectiveValue(identityEdit.correction?.description)).isEqualTo("Corrected item")
        assertThat(identityEdit.packageText.effectiveValue(identityEdit.correction?.packageText)).isEqualTo("12 pack")

        val numericCorrection = ParsedInvoiceLineCorrection(
            quantity = Correction(BigDecimal("5")),
            unitPrice = Correction(BigDecimal("2.50")),
            lineTotal = Correction(BigDecimal("12.50"))
        )
        repository.updateParsedLine(receiptId, 0, false, numericCorrection)
        val numericEdit = awaitEmission()
        val editedLine = numericEdit.lines.single { it.index == 0 }
        assertThat(editedLine.quantity.effectiveValue(editedLine.correction?.quantity)).isEqualTo(BigDecimal("5"))
        assertThat(editedLine.unitPrice.effectiveValue(editedLine.correction?.unitPrice)).isEqualTo(BigDecimal("2.50"))
        assertThat(editedLine.lineTotal.effectiveValue(editedLine.correction?.lineTotal)).isEqualTo(BigDecimal("12.50"))
        val correctedProposal = generateProposalUseCase.execute(receiptId)!!
        assertThat(correctedProposal.lines.single { it.lineIndex == 0 }.quantityEntered).isEqualTo(BigDecimal("5"))
        assertThat(correctedProposal.lines.single { it.lineIndex == 0 }.lineTotal).isEqualTo(BigDecimal("12.50"))

        repository.updateParsedLine(receiptId, 0, true, numericCorrection)
        val ignored = awaitEmission()
        assertThat(ignored.lines.count { !it.isIgnored }).isEqualTo(1)
        assertThat(generateProposalUseCase.execute(receiptId)!!.lines.map { it.lineIndex }).doesNotContain(0)

        repository.updateParsedLine(receiptId, 0, false, numericCorrection)
        val included = awaitEmission()
        assertThat(included.lines.single { it.index == 0 }.isIgnored).isFalse()

        repository.updateParsedLine(receiptId, 0, false, null)
        val reset = awaitEmission().lines.single { it.index == 0 }
        assertThat(reset.correction).isNull()
        assertThat(reset.description.effectiveValue(reset.correction?.description)).isEqualTo("Item 0")
        assertThat(reset.quantity.effectiveValue(reset.correction?.quantity)).isEqualTo(BigDecimal.ONE)
        assertThat(reset.lineTotal.effectiveValue(reset.correction?.lineTotal)).isEqualTo(BigDecimal.TEN)
        observer.cancel()
    }

    private suspend fun seedPurchaseWithParseResult(lineCount: Int = 1): PurchaseReceiptId {
        val supplierId = SupplierId(TestSeeder.SUPPLIER_ID)
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, supplierId, "INV-1", Instant.now(), null))
        
        val storedDoc = com.miara.cuentame.test.TestDocumentFixture.storeTestDocument(context, documentStore, receiptId)
        val sha = com.miara.cuentame.test.TestDocumentFixture.calculateSha256(documentStore.getFile(storedDoc.location))
        
        repository.attachDocument(receiptId, storedDoc.location, storedDoc.displayName)

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
            expectedAttachmentPath = storedDoc.location,
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
