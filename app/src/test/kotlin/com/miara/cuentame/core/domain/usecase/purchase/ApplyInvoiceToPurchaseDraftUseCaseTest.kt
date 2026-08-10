package com.miara.cuentame.core.domain.usecase.purchase

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.PurchaseInvoiceMaterializationDao
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceLineProposal
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ApplyInvoiceToPurchaseDraftUseCaseTest {

    private val database = mockk<RestaurantInventoryDatabase>()
    private val purchaseRepository = mockk<PurchaseRepository>()
    private val idGenerator = mockk<IdGenerator>()
    private val timeProvider = mockk<TimeProvider>()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val purchaseDao = mockk<PurchaseDao>()
    private val materializationDao = mockk<PurchaseInvoiceMaterializationDao>()

    private lateinit var useCase: ApplyInvoiceToPurchaseDraftUseCase

    private val receiptId = PurchaseReceiptId("r1")
    private val restaurantId = RestaurantId("rest1")

    @Before
    fun setup() {
        useCase = ApplyInvoiceToPurchaseDraftUseCase(
            database,
            purchaseRepository,
            idGenerator,
            timeProvider,
            json
        )
        
        every { database.purchaseDao() } returns purchaseDao
        every { database.purchaseInvoiceMaterializationDao() } returns materializationDao
        
        // Mock withTransaction to just execute the block
        val transactionBlock = slot<suspend () -> Any>()
        coEvery { database.withTransaction<Any>(capture(transactionBlock)) } coAnswers {
            transactionBlock.captured.invoke()
        }
    }

    @Test
    fun `successfully applies proposal to draft`() = runBlocking {
        // Arrange
        val proposal = PurchaseInvoiceDraftProposal(
            purchaseReceiptId = receiptId,
            parseResultId = "p1",
            sourceDocumentSha256 = "sha",
            supplierProposal = null,
            invoiceNumber = "INV1",
            invoiceDate = LocalDate.now(),
            subtotal = BigDecimal("100"),
            discount = null,
            fees = null,
            tax = null,
            total = BigDecimal("100"),
            lines = listOf(
                PurchaseInvoiceLineProposal(
                    lineIndex = 0,
                    ingredientId = IngredientId("i1"),
                    ingredientName = "Tomato",
                    unitOptionId = IngredientUnitOptionId("u1"),
                    unitOptionName = "CS",
                    areaId = InventoryAreaId("a1"),
                    areaName = "Walk-In",
                    quantityEntered = BigDecimal("2"),
                    quantityBase = BigDecimal("2"),
                    unitPrice = BigDecimal("50"),
                    lineTotal = BigDecimal("100")
                )
            )
        )

        val receipt = PurchaseReceipt(
            id = receiptId,
            restaurantId = restaurantId,
            purchaseDate = Instant.now(),
            status = DocumentStatus.DRAFT,
            attachmentPath = "path",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        coEvery { purchaseRepository.getReceipt(receiptId) } returns receipt
        every { purchaseRepository.observeOcrResult(receiptId) } returns flowOf(mockk<PurchaseInvoiceOcrResult> {
            every { sourceDocumentSha256 } returns "sha"
        })
        every { purchaseRepository.observeParseResult(receiptId) } returns flowOf(mockk<PurchaseInvoiceParseResult> {
            every { id } returns "p1"
        })
        coEvery { purchaseRepository.updateDraft(any()) } just Runs
        
        coEvery { materializationDao.getApplicationForReceipt(any()) } returns null
        every { idGenerator.newId() } returns "new_id"
        coEvery { materializationDao.insertApplication(any()) } just Runs
        
        coEvery { purchaseDao.getLinesForReceipt(any()) } returns emptyList()
        coEvery { materializationDao.getLineOrigins(any()) } returns emptyList()
        every { timeProvider.now() } returns Instant.now()
        coEvery { purchaseDao.insertLine(any()) } just Runs
        coEvery { materializationDao.insertLineOrigins(any()) } just Runs

        // Act
        val result = useCase.execute(proposal)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { purchaseRepository.updateDraft(any()) }
        coVerify { purchaseDao.insertLine(any()) }
    }
}
