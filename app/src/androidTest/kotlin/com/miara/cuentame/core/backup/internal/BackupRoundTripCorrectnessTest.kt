package com.miara.cuentame.core.backup.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupSnapshotSource
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityValidator
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.model.backup.BackupManifest
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
class BackupRoundTripCorrectnessTest {

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
    lateinit var snapshotSource: BackupSnapshotSource

    @Inject
    lateinit var applier: RoomRestoreDatabaseApplier

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
    fun materialization_provenance_survives_real_path_round_trip() = runBlocking {
        // 1. Seed Data and Materialize
        val receiptId = seedPurchaseWithParseResult()
        val proposal = generateProposalUseCase.execute(receiptId)!!
        applyInvoiceUseCase.execute(proposal)
        
        val appId = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)!!.id
        val purchaseLines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(purchaseLines).hasSize(1)
        val purchaseLineId = purchaseLines[0].id

        // 2. Take Snapshot (REAL PATH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        val snapshot = snapshotResult.dto
        
        assertThat(snapshot.purchaseInvoiceDraftApplications).isNotEmpty()
        assertThat(snapshot.purchaseInvoiceLineOrigins).isNotEmpty()

        // 3. Clear and Restore
        testStateManager.resetAll()
        
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-08-10T00:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 10,
            restaurantId = restId.value,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = listOf("data")
        )
        
        applier.replaceWithBackup(snapshot, manifest)

        // Run integrity validator against restored data
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        BackupSnapshotIntegrityValidator.validate(restoredSnapshot, manifest).getOrThrow() // Should not throw

        // 4. Verify Survivors
        val restoredApp = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receiptId.value)
        assertThat(restoredApp).isNotNull()
        assertThat(restoredApp!!.id).isEqualTo(appId)

        val restoredOrigins = database.purchaseInvoiceMaterializationDao().getLineOrigins(appId)
        assertThat(restoredOrigins).hasSize(1)
        assertThat(restoredOrigins[0].purchaseLineId).isEqualTo(purchaseLineId)
    }

    private suspend fun seedPurchaseWithParseResult(): PurchaseReceiptId {
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
            lines = listOf(
                ParsedInvoiceLineCandidate(
                    index = 0,
                    vendorCode = ParsedField("V1", "V1", 0.9f),
                    description = ParsedField("Item 1", "Item 1", 0.9f),
                    quantity = ParsedField("1", BigDecimal.ONE, 0.9f),
                    packageText = ParsedField("EA", "EA", 0.9f),
                    unitPrice = ParsedField("10", BigDecimal.TEN, 0.9f),
                    lineTotal = ParsedField("10", BigDecimal.TEN, 0.9f),
                    confidence = 0.9f
                )
            ),
            confidence = 0.9f
        )
        repository.saveParseResult(receiptId, "ocr-" + receiptId.value, sha, parseResult)

        val matches = listOf(
            PurchaseInvoiceLineMatch(
                parseResultId = parseResult.id,
                lineIndex = 0,
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
        )
        repository.saveLineMatchesForReceipt(receiptId, parseResult.id, matches)
        
        return receiptId
    }
}
