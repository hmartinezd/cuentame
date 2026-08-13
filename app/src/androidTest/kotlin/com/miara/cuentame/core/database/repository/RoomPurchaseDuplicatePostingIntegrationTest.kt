package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParseResultEntity
import com.miara.cuentame.core.database.entity.SupplierEntity
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceCandidate
import com.miara.cuentame.core.model.purchase.DuplicateInvoicePostingException
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceType
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

/** Real Room coverage for duplicate admission at the authoritative posting transaction. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomPurchaseDuplicatePostingIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var database: RestaurantInventoryDatabase
    @Inject lateinit var repository: RoomPurchaseRepository
    @Inject lateinit var testStateManager: TestStateManager

    private val restaurantId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val supplierOne = SupplierId(TestSeeder.SUPPLIER_ID)

    @Before
    fun setUp() {
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
    fun sameDocumentSha_blocksAtomicallyAtPostingBoundary() = runBlocking {
        val existing = createReadyReceipt(supplierOne, "DOC-A", "sha-x")
        repository.post(existing)
        val duplicate = createReadyReceipt(supplierOne, "DOC-B", "sha-x")

        val candidate = blockedCandidate(duplicate)

        assertThat(candidate.type).isEqualTo(DuplicateInvoiceType.SAME_DOCUMENT)
        assertThat(candidate.existingReceiptId).isEqualTo(existing)
        assertThat(candidate.sourceSha256).isEqualTo("sha-x")
        assertBlockedAndAtomic(duplicate)
    }

    @Test
    fun normalizedInvoice_blocksSameSupplier_butAllowsDifferentSupplier() = runBlocking {
        val existing = createReadyReceipt(supplierOne, "INV 100", "sha-a")
        repository.post(existing)
        val sameSupplier = createReadyReceipt(supplierOne, "inv100", "sha-b")

        val candidate = blockedCandidate(sameSupplier)
        assertThat(candidate.type).isEqualTo(DuplicateInvoiceType.SAME_SUPPLIER_INVOICE_NUMBER)
        assertThat(candidate.existingReceiptId).isEqualTo(existing)
        assertThat(candidate.normalizedInvoiceNumber).isEqualTo("INV100")
        assertBlockedAndAtomic(sameSupplier)

        val supplierTwo = createSupplier("supplier-two", "Second Supplier")
        val otherSupplier = createReadyReceipt(supplierTwo, "INV100", "sha-c")
        repository.post(otherSupplier)

        assertThat(repository.getReceipt(otherSupplier)?.status).isEqualTo(DocumentStatus.POSTED)
        assertInventoryEffect(otherSupplier, expectedMovements = 1, expectedQuantity = "2")
    }

    @Test
    fun staleSafePrecheck_cannotAuthorizeDuplicateInsertedBeforePost() = runBlocking {
        val receipt = createReadyReceipt(supplierOne, "STALE 42", "sha-stale-b")

        // T1: persisted database state has no duplicate and posting would be admissible.
        assertThat(DuplicateInvoiceDetector(database.purchaseDao()).find(
            restaurantId.value, receipt.value, supplierOne.value, "STALE 42", "sha-stale-b"
        )).isNull()

        // T2: another receipt becomes authoritative before the real transaction begins.
        val newlyExisting = createReadyReceipt(supplierOne, "NOT-YET-DUPLICATE", "sha-stale-a")
        repository.post(newlyExisting)
        val persisted = database.purchaseDao().getReceiptById(newlyExisting.value)!!
        database.purchaseDao().updateReceipt(persisted.copy(invoiceNumber = "stale42", updatedAt = 2))

        // T3: production post re-reads Room state inside its transaction.
        val candidate = blockedCandidate(receipt)
        assertThat(candidate.type).isEqualTo(DuplicateInvoiceType.SAME_SUPPLIER_INVOICE_NUMBER)
        assertThat(candidate.existingReceiptId).isEqualTo(newlyExisting)
        assertBlockedAndAtomic(receipt)
    }

    @Test
    fun explicitOverride_thenNormalRetry_postsExactlyOnce_andRemainsIdempotent() = runBlocking {
        val existing = createReadyReceipt(supplierOne, "RETRY 7", "sha-retry-a")
        repository.post(existing)
        val receipt = createReadyReceipt(supplierOne, "retry7", "sha-retry-b")
        val candidate = blockedCandidate(receipt)

        repository.acceptDuplicateForPosting(candidate)
        val application = database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receipt.value)!!
        assertThat(application.duplicateOverrideType).isEqualTo(candidate.type.name)
        assertThat(application.duplicateExistingReceiptId).isEqualTo(existing.value)
        assertThat(application.duplicateNormalizedInvoiceNumber).isEqualTo("RETRY7")
        assertThat(application.duplicateOverriddenAt).isNotNull()

        repository.post(receipt)
        assertThat(repository.getReceipt(receipt)?.status).isEqualTo(DocumentStatus.POSTED)
        assertInventoryEffect(receipt, expectedMovements = 1, expectedQuantity = "2")

        repository.post(receipt)
        assertInventoryEffect(receipt, expectedMovements = 1, expectedQuantity = "2")
    }

    @Test
    fun changedFingerprint_rejectsStaleAcceptance_andOldOverrideCannotAuthorizeIt() = runBlocking {
        val firstExisting = createReadyReceipt(supplierOne, "OLD 1", "sha-old-a")
        repository.post(firstExisting)
        val receipt = createReadyReceipt(supplierOne, "old1", "sha-old-b")
        val oldCandidate = blockedCandidate(receipt)

        val secondExisting = createReadyReceipt(supplierOne, "NEW 2", "sha-new-a")
        repository.post(secondExisting)
        repository.updateDraft(UpdatePurchaseDraftCommand(receipt, supplierOne, "new2", Instant.ofEpochMilli(1_000), null))

        val staleFailure = runCatching { repository.acceptDuplicateForPosting(oldCandidate) }.exceptionOrNull()
        assertThat(staleFailure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(database.purchaseInvoiceMaterializationDao().getApplicationForReceipt(receipt.value)!!.duplicateOverrideType).isNull()

        val newCandidate = blockedCandidate(receipt)
        assertThat(newCandidate).isNotEqualTo(oldCandidate)
        assertThat(newCandidate.existingReceiptId).isEqualTo(secondExisting)
        assertThat(newCandidate.normalizedInvoiceNumber).isEqualTo("NEW2")
        assertBlockedAndAtomic(receipt)
    }

    @Test
    fun acceptedOverride_isNarrow_whenDuplicateChangesAfterAcceptance() = runBlocking {
        val firstExisting = createReadyReceipt(supplierOne, "FIRST 1", "sha-first-a")
        repository.post(firstExisting)
        val receipt = createReadyReceipt(supplierOne, "first1", "sha-first-b")
        repository.acceptDuplicateForPosting(blockedCandidate(receipt))

        val secondExisting = createReadyReceipt(supplierOne, "SECOND 2", "sha-second-a")
        repository.post(secondExisting)
        repository.updateDraft(UpdatePurchaseDraftCommand(receipt, supplierOne, "second2", Instant.ofEpochMilli(1_000), null))

        val changed = blockedCandidate(receipt)
        assertThat(changed.existingReceiptId).isEqualTo(secondExisting)
        assertThat(changed.normalizedInvoiceNumber).isEqualTo("SECOND2")
        assertBlockedAndAtomic(receipt)
    }

    @Test
    fun ordinarySameReceiptPost_isIdempotent_withoutCrossReceiptConflict() = runBlocking {
        val receipt = createReadyReceipt(supplierOne, "UNIQUE-9", "sha-unique")

        repository.post(receipt)
        repository.post(receipt)

        assertThat(repository.getReceipt(receipt)?.status).isEqualTo(DocumentStatus.POSTED)
        assertInventoryEffect(receipt, expectedMovements = 1, expectedQuantity = "2")
    }

    private suspend fun createReadyReceipt(
        supplierId: SupplierId,
        invoiceNumber: String,
        sha: String
    ): PurchaseReceiptId {
        val receiptId = repository.createDraft(
            CreatePurchaseDraftCommand(restaurantId, supplierId, invoiceNumber, Instant.ofEpochMilli(1_000), null)
        )
        repository.saveLine(
            SavePurchaseLineCommand(
                receiptId, null, IngredientId(TestSeeder.ING_ID), InventoryAreaId(TestSeeder.AREA_ID),
                IngredientUnitOptionId(TestSeeder.OPTION_ID), BigDecimal("2"), BigDecimal("10"), null
            )
        )
        val suffix = receiptId.value
        val ocrId = "ocr-$suffix"
        val parseId = "parse-$suffix"
        database.purchaseOcrDao().insertOcrResult(
            PurchaseInvoiceOcrResultEntity(ocrId, receiptId.value, sha, "application/pdf", "test", 1, 1, "", 1)
        )
        database.purchaseParseDao().insertParseResult(
            PurchaseInvoiceParseResultEntity(
                parseId, receiptId.value, ocrId, sha, "test", 1, "{}", "{}", null, "[]", 1, 1
            )
        )
        database.purchaseInvoiceMaterializationDao().upsertApplication(
            PurchaseInvoiceDraftApplicationEntity("application-$suffix", receiptId.value, parseId, sha, "fixture", 1)
        )
        return receiptId
    }

    private suspend fun createSupplier(id: String, name: String): SupplierId {
        database.supplierDao().insert(
            SupplierEntity(id, restaurantId.value, name, name.lowercase(), null, null, null, true, 0, 0, null)
        )
        return SupplierId(id)
    }

    private suspend fun blockedCandidate(receiptId: PurchaseReceiptId): DuplicateInvoiceCandidate {
        val failure = runCatching { repository.post(receiptId) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(DuplicateInvoicePostingException::class.java)
        return (failure as DuplicateInvoicePostingException).candidate
    }

    private suspend fun assertBlockedAndAtomic(receiptId: PurchaseReceiptId) {
        assertThat(repository.getReceipt(receiptId)?.status).isEqualTo(DocumentStatus.DRAFT)
        assertInventoryEffect(receiptId, expectedMovements = 0, expectedQuantity = "0")
        val lines = database.purchaseDao().getLinesForReceipt(receiptId.value)
        assertThat(lines).hasSize(1)
        assertThat(lines.single().quantityEntered).isEqualTo("2")
        assertThat(lines.single().lineTotal).isEqualTo("10")
    }

    private suspend fun assertInventoryEffect(
        receiptId: PurchaseReceiptId,
        expectedMovements: Int,
        expectedQuantity: String
    ) {
        val movements = database.inventoryMovementDao().getBySourceDocument(
            SourceDocumentType.PURCHASE_RECEIPT.name, receiptId.value
        )
        assertThat(movements).hasSize(expectedMovements)
        val actualQuantity = movements.fold(BigDecimal.ZERO) { total, movement ->
            total + BigDecimal(movement.quantityBaseSigned)
        }
        assertThat(actualQuantity.compareTo(BigDecimal(expectedQuantity))).isEqualTo(0)
    }
}
