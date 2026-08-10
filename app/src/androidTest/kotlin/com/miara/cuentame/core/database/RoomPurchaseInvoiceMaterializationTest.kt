package com.miara.cuentame.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceLineOriginEntity
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.ParsedField
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
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
class RoomPurchaseInvoiceMaterializationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RestaurantInventoryDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

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
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun verifyMigration9To10() {
        val dbName = "migration-9-10-test"
        var db = migrationHelper.createDatabase(dbName, 9)
        
        // Seed baseline for v9
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('r1', 'Rest', 'USD', 'en', 0, 0)")
        db.execSQL("INSERT INTO purchase_receipts (id, restaurantId, purchaseDate, status, createdAt, updatedAt) VALUES ('pr1', 'r1', 0, 'DRAFT', 0, 0)")
        db.execSQL("INSERT INTO purchase_invoice_ocr_results (id, purchaseReceiptId, sourceDocumentSha256, sourceMimeType, engine, evidenceSchemaVersion, pageCount, fullText, processedAt) VALUES ('ocr1', 'pr1', 'sha', 'pdf', 't', 1, 1, '', 0)")
        db.execSQL("INSERT INTO purchase_invoice_parse_results (id, purchaseReceiptId, ocrResultId, sourceDocumentSha256, parserEngine, parserSchemaVersion, headerEvidenceJson, totalsEvidenceJson, warningsJson, processedAt) VALUES ('p1', 'pr1', 'ocr1', 'sha', 'e', 1, '{}', '{}', '[]', 0)")
        
        db.execSQL("INSERT INTO purchase_invoice_draft_applications (id, purchaseReceiptId, parseResultId, sourceDocumentSha256, sourceStateFingerprint, appliedAt) VALUES ('app1', 'pr1', 'p1', 'sha', 'f1', 0)")
        
        db.execSQL("INSERT INTO purchase_lines (id, purchaseReceiptId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, lineTotal, unitCostBase, createdAt, updatedAt) VALUES ('l1', 'pr1', 'i1', 'a1', 'o1', '1', '1', '10', '10', 0, 0)")
        db.execSQL("INSERT INTO purchase_invoice_line_origins (purchaseLineId, applicationId, sourceLineIndex, sourceStateFingerprint, lastMaterializedSnapshotJson) VALUES ('l1', 'app1', 0, 'f1', '{}')")
        
        db.close()

        // Migrate to 10
        db = migrationHelper.runMigrationsAndValidate(dbName, 10, true, RestaurantInventoryDatabase.MIGRATION_9_10)
        
        // Verify unique constraint exists by trying to insert a duplicate (should fail)
        try {
            db.execSQL("INSERT INTO purchase_invoice_line_origins (purchaseLineId, applicationId, sourceLineIndex, sourceStateFingerprint, lastMaterializedSnapshotJson) VALUES ('l2', 'app1', 0, 'f1', '{}')")
            assertThat(false).isTrue() // Should not reach here
        } catch (e: Exception) {
            assertThat(e.message).contains("UNIQUE constraint failed")
        }
        
        db.close()
    }

    @Test
    fun upsertBehavior_applicationAndOrigins() = runBlocking {
        val receiptId = seedPurchaseWithParseResult()
        
        // 1. First Apply
        val proposal1 = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposal1)
        
        val app1 = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)!!
        val origins1 = database.purchaseInvoiceMaterializationDao().getLineOrigins(app1.id)
        assertThat(origins1).hasSize(1)
        val firstAppliedAt = app1.appliedAt

        // 2. Second Apply (Same Proposal)
        Thread.sleep(10) // Ensure different timestamp if not using mocked time
        applyInvoiceUseCase.execute(proposal1)
        
        val app2 = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)!!
        assertThat(app2.id).isEqualTo(app1.id) // UPSERT should keep ID
        assertThat(app2.appliedAt).isGreaterThan(firstAppliedAt)
        
        val origins2 = database.purchaseInvoiceMaterializationDao().getLineOrigins(app2.id)
        assertThat(origins2).hasSize(1)
        assertThat(origins2[0].purchaseLineId).isEqualTo(origins1[0].purchaseLineId)
    }

    @Test
    fun reconciliation_deletesRemovedLines() = runBlocking {
        val receiptId = seedPurchaseWithParseResult(lineCount = 2)
        
        // 1. Apply both lines
        val proposalFull = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposalFull)
        
        assertThat(database.purchaseDao().getLinesForReceipt(receiptId.value)).hasSize(2)
        val appId = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)!!.id
        assertThat(database.purchaseInvoiceMaterializationDao().getLineOrigins(appId)).hasSize(2)

        // 2. Mock parse result to ignore one line
        repository.updateParsedLine(receiptId, 1, isIgnored = true, correction = null)
        
        // 3. Apply again
        val proposalReduced = generateProposalUseCase.execute(receiptId)!!
        assertThat(proposalReduced.lines).hasSize(1)
        
        applyInvoiceUseCase.execute(proposalReduced)
        
        // 4. Verify reconciliation
        assertThat(database.purchaseDao().getLinesForReceipt(receiptId.value)).hasSize(1)
        assertThat(database.purchaseInvoiceMaterializationDao().getLineOrigins(appId)).hasSize(1)
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
                supplierId = null,
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
