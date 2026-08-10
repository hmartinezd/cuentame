package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
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
        val parseA = mockParseResult("parse-A")
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", parseA)

        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // 2. Confirmation succeeds for Parse A
        repository.confirmInvoiceLineMatch(receiptId, "parse-A", null, 0, ingId, optId, areaId, false)
        
        val matches = database.purchaseInvoiceLineMatchDao().getMatchesForParseResult("parse-A")
        assertThat(matches).hasSize(1)
        assertThat(matches[0].status).isEqualTo(InvoiceLineMatchStatus.CONFIRMED.name)

        // 3. Replace Parse A with Parse B
        val parseB = mockParseResult("parse-B")
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", parseB)

        // 4. Confirmation for Parse A now fails
        try {
            repository.confirmInvoiceLineMatch(receiptId, "parse-A", null, 0, ingId, optId, areaId, false)
            error("Should have thrown ValidationError.ParseResultChanged")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(ValidationError.ParseResultChanged::class.java)
        }

        // 5. Verify no confirmed match written into Parse B automatically
        val matchesB = database.purchaseInvoiceLineMatchDao().getMatchesForParseResult("parse-B")
        assertThat(matchesB).isEmpty()
    }

    @Test
    fun confirmInvoiceLineMatch_requiresUnitAndArea() = runBlocking {
        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null))
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", mockParseResult("p1"))
        
        val ingId = IngredientId(TestSeeder.ING_ID)
        
        try {
            repository.confirmInvoiceLineMatch(receiptId, "p1", null, 0, ingId, null, null, false)
            error("Should have thrown ValidationError.InvalidMatchStatus")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(ValidationError.InvalidMatchStatus::class.java)
        }
    }

    @Test
    fun confirmInvoiceLineMatch_revalidatesSupplierId() = runBlocking {
        val supplierA = SupplierId(idGenerator.newId())
        val supplierB = SupplierId(idGenerator.newId())
        database.supplierDao().insert(com.miara.cuentame.core.database.entity.SupplierEntity(supplierA.value, restId.value, "Sup A", "sup-a", null, null, null, true, 0, 0, null))
        database.supplierDao().insert(com.miara.cuentame.core.database.entity.SupplierEntity(supplierB.value, restId.value, "Sup B", "sup-b", null, null, null, true, 0, 0, null))

        val receiptId = repository.createDraft(CreatePurchaseDraftCommand(restId, supplierA, "INV-1", Instant.now(), null))
        repository.saveParseResult(receiptId, "ocr-1", "sha-1", mockParseResult("p1"))
        
        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // Purchase has Supplier A. We call with expected Supplier A -> Success
        repository.confirmInvoiceLineMatch(receiptId, "p1", supplierA, 0, ingId, optId, areaId, false)

        // Change Purchase Supplier to B
        repository.updateDraft(com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand(receiptId, supplierB, "INV-1", Instant.now(), null))

        // Call with expected Supplier A -> Failure
        try {
            repository.confirmInvoiceLineMatch(receiptId, "p1", supplierA, 0, ingId, optId, areaId, false)
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
