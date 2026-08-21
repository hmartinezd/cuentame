package com.venkoi.restaurantops.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.domain.repository.CreatePurchaseDraftCommand
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import com.venkoi.restaurantops.core.model.purchase.InvoiceLineMatchStatus
import com.venkoi.restaurantops.core.ocr.parser.ParsedField
import com.venkoi.restaurantops.core.ocr.parser.ParsedInvoiceLineCandidate
import com.venkoi.restaurantops.core.ocr.parser.PurchaseInvoiceParseResult
import com.venkoi.restaurantops.test.TestSeeder
import com.venkoi.restaurantops.test.TestStateManager
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
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomPurchaseRepositoryIntegrityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: PurchaseRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var idGenerator: IdGenerator

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
    fun confirmInvoiceLineMatch_revalidatesParseResultId() = runBlocking {
        // 1. Setup Purchase and Parse A
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null))
        
        // Seed OCR Result required by saveParseResult
        database.purchaseOcrDao().insertOcrResult(com.venkoi.restaurantops.core.database.entity.PurchaseInvoiceOcrResultEntity(
            "ocr-1", receiptId.value, "sha-1", "application/pdf", "test", 1, 1, "text", 0
        ))

        val parseA = mockParseResult("parse-A")
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", parseA)
        val actualParseIdA = database.purchaseParseDao().getParseResultIdForReceipt(receiptId.value)!!

        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // 2. Confirmation succeeds for Parse A
        repository.confirmInvoiceLineMatch(receiptId, actualParseIdA, null, 0, ingId, optId, areaId, false)
        
        val matches = database.purchaseInvoiceLineMatchDao().getMatchesForParseResult(actualParseIdA)
        assertThat(matches).hasSize(1)
        assertThat(matches[0].status).isEqualTo(InvoiceLineMatchStatus.CONFIRMED)

        // 3. Replace Parse A with Parse B
        val parseB = mockParseResult("parse-B")
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", parseB)
        val actualParseIdB = database.purchaseParseDao().getParseResultIdForReceipt(receiptId.value)!!

        // 4. Confirmation for Parse A now fails
        try {
            repository.confirmInvoiceLineMatch(receiptId, actualParseIdA, null, 0, ingId, optId, areaId, false)
            error("Should have thrown ValidationError.ParseResultChanged")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(ValidationError.ParseResultChanged::class.java)
        }

        // 5. Verify no confirmed match written into Parse B automatically
        val matchesB = database.purchaseInvoiceLineMatchDao().getMatchesForParseResult(actualParseIdB)
        assertThat(matchesB).isEmpty()
    }

    @Test
    fun confirmInvoiceLineMatch_requiresUnitAndArea() = runBlocking {
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null))
        database.purchaseOcrDao().insertOcrResult(com.venkoi.restaurantops.core.database.entity.PurchaseInvoiceOcrResultEntity(
            "ocr-1", receiptId.value, "sha-1", "application/pdf", "test", 1, 1, "text", 0
        ))
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", mockParseResult("p1"))
        val actualParseId = database.purchaseParseDao().getParseResultIdForReceipt(receiptId.value)!!
        
        val ingId = IngredientId(TestSeeder.ING_ID)
        
        try {
            repository.confirmInvoiceLineMatch(receiptId, actualParseId, null, 0, ingId, null, null, false)
            error("Should have thrown ValidationError.InvalidMatchStatus")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(ValidationError.InvalidMatchStatus::class.java)
        }
    }

    @Test
    fun confirmInvoiceLineMatch_revalidatesSupplierId() = runBlocking {
        val supplierA = SupplierId(idGenerator.newId())
        val supplierB = SupplierId(idGenerator.newId())
        database.supplierDao().insert(com.venkoi.restaurantops.core.database.entity.SupplierEntity(supplierA.value, restId.value, "Sup A", "sup-a", null, null, null, true, 0, 0, null))
        database.supplierDao().insert(com.venkoi.restaurantops.core.database.entity.SupplierEntity(supplierB.value, restId.value, "Sup B", "sup-b", null, null, null, true, 0, 0, null))

        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, supplierA, "INV-1", Instant.now(), null))
        database.purchaseOcrDao().insertOcrResult(com.venkoi.restaurantops.core.database.entity.PurchaseInvoiceOcrResultEntity(
            "ocr-1", receiptId.value, "sha-1", "application/pdf", "test", 1, 1, "text", 0
        ))
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", mockParseResult("p1"))
        val actualParseId = database.purchaseParseDao().getParseResultIdForReceipt(receiptId.value)!!
        
        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // Purchase has Supplier A. We call with expected Supplier A -> Success
        repository.confirmInvoiceLineMatch(receiptId, actualParseId, supplierA, 0, ingId, optId, areaId, false)

        // Change Purchase Supplier to B
        repository.updateDraft(com.venkoi.restaurantops.core.domain.repository.UpdatePurchaseDraftCommand(receiptId, supplierB, "INV-1", Instant.now(), null))

        // Call with expected Supplier A -> Failure
        try {
            repository.confirmInvoiceLineMatch(receiptId, actualParseId, supplierA, 0, ingId, optId, areaId, false)
            error("Should have thrown ValidationError")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(ValidationError.SupplierOwnershipMismatch::class.java)
        }
    }

    private fun mockParseResult(id: String) = PurchaseInvoiceParseResult(
        id = id,
        supplierNameCandidate = ParsedField("Sup", "Sup", 0.9f),
        invoiceNumber = ParsedField("INV-1", "INV-1", 0.9f),
        invoiceDate = ParsedField(null, null, null),
        subtotal = ParsedField(null, null, null),
        discount = ParsedField(null, null, null),
        fees = ParsedField(null, null, null),
        tax = ParsedField(null, null, null),
        total = ParsedField("100", BigDecimal("100"), 0.9f),
        currency = ParsedField("USD", "USD", 0.9f),
        lines = listOf(
            ParsedInvoiceLineCandidate(
                index = 0,
                vendorCode = ParsedField("C1", "C1", 0.9f),
                description = ParsedField("D1", "D1", 0.9f),
                quantity = ParsedField("1", BigDecimal.ONE, 0.9f),
                packageText = ParsedField(null, null, null),
                unitPrice = ParsedField("100", BigDecimal("100"), 0.9f),
                lineTotal = ParsedField("100", BigDecimal("100"), 0.9f),
                confidence = 0.9f
            )
        ),
        confidence = 0.9f
    )
}
