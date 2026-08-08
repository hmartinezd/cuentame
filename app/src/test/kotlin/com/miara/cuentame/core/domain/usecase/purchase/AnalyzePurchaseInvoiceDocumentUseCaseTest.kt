package com.miara.cuentame.core.domain.usecase.purchase

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.PurchasePdfDocumentInfo
import com.miara.cuentame.core.backup.api.PurchasePdfPageRenderResult
import com.miara.cuentame.core.backup.api.PurchasePdfRenderer
import com.miara.cuentame.core.backup.api.StoredPurchaseDocument
import com.miara.cuentame.core.common.hash.HashUtils
import com.miara.cuentame.core.common.image.SafeImageDecoder
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.ocr.FakePurchaseInvoiceOcrEngine
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrFailure
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class AnalyzePurchaseInvoiceDocumentUseCaseTest {

    private val repository = mockk<PurchaseRepository>(relaxed = true)
    private val documentStore = mockk<PurchaseDocumentStore>()
    private val pdfRenderer = mockk<PurchasePdfRenderer>()
    private val ocrEngine = FakePurchaseInvoiceOcrEngine()
    private val parseUseCase = mockk<ParsePurchaseInvoiceUseCase>(relaxed = true)
    private val idGenerator = mockk<IdGenerator>()
    private val timeProvider = mockk<TimeProvider>()

    private lateinit var useCase: AnalyzePurchaseInvoiceDocumentUseCase

    @Before
    fun setUp() {
        mockkObject(SafeImageDecoder)
        useCase = AnalyzePurchaseInvoiceDocumentUseCase(
            repository, documentStore, pdfRenderer, ocrEngine, parseUseCase, idGenerator, timeProvider
        )
        every { timeProvider.now() } returns Instant.parse("2026-08-07T12:00:00Z")
        every { idGenerator.newId() } returns "ocr-123"
    }

    @After
    fun tearDown() {
        unmockkObject(SafeImageDecoder)
    }

    @Test
    fun successfulOnePagePdfOcr() = runTest {
        val receiptId = PurchaseReceiptId("r1")
        val receipt = createReceipt(receiptId, "path/to/doc.pdf")
        coEvery { repository.getReceipt(receiptId) } returns receipt
        coEvery { documentStore.inspect("path/to/doc.pdf") } returns StoredPurchaseDocument("path/to/doc.pdf", "doc.pdf", "application/pdf", 100)
        coEvery { documentStore.open("path/to/doc.pdf") } returns ByteArrayInputStream(ByteArray(10))
        coEvery { documentStore.getFile("path/to/doc.pdf") } returns java.io.File("path/to/doc.pdf")
        coEvery { pdfRenderer.inspect(any()) } returns PurchasePdfDocumentInfo(pageCount = 1)
        
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.width } returns 100
        every { bitmap.height } returns 200
        coEvery { pdfRenderer.renderPage(any(), 0, any()) } returns PurchasePdfPageRenderResult.Success(bitmap)
        coEvery { parseUseCase.execute(receiptId) } returns ParsePurchaseInvoiceResult.Success(mockk(relaxed = true))

        val results = useCase(receiptId).toList()

        assertThat(results).contains(AnalyzePurchaseInvoiceResult.Success)
        coVerify { repository.saveOcrResult(any(), any(), "path/to/doc.pdf", any()) }
    }

    @Test
    fun failsWhenNoDocumentAttached() = runTest {
        val receiptId = PurchaseReceiptId("r1")
        coEvery { repository.getReceipt(receiptId) } returns createReceipt(receiptId, null)

        val results = useCase(receiptId).toList()

        assertThat(results.last()).isInstanceOf(AnalyzePurchaseInvoiceResult.Failure::class.java)
        val failure = results.last() as AnalyzePurchaseInvoiceResult.Failure
        assertThat(failure.reason).isEqualTo(PurchaseInvoiceOcrFailure.NoDocument)
    }

    @Test
    fun atomicPersistenceFailureOnSecondPageKeepsOldResult() = runTest {
        val receiptId = PurchaseReceiptId("r1")
        val receipt = createReceipt(receiptId, "path/to/doc.pdf")
        coEvery { repository.getReceipt(receiptId) } returns receipt
        coEvery { documentStore.inspect("path/to/doc.pdf") } returns StoredPurchaseDocument("path/to/doc.pdf", "doc.pdf", "application/pdf", 100)
        coEvery { documentStore.open("path/to/doc.pdf") } returns ByteArrayInputStream(ByteArray(10))
        coEvery { documentStore.getFile("path/to/doc.pdf") } returns java.io.File("path/to/doc.pdf")
        coEvery { pdfRenderer.inspect(any()) } returns PurchasePdfDocumentInfo(pageCount = 2)

        val bitmap = mockk<Bitmap>(relaxed = true)
        coEvery { pdfRenderer.renderPage(any(), 0, any()) } returns PurchasePdfPageRenderResult.Success(bitmap)
        coEvery { pdfRenderer.renderPage(any(), 1, any()) } returns PurchasePdfPageRenderResult.Failure(com.miara.cuentame.core.backup.api.PurchasePdfRenderFailure.RenderFailed)

        val results = useCase(receiptId).toList()

        assertThat(results.last()).isEqualTo(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.RenderFailed))
        coVerify(exactly = 0) { repository.saveOcrResult(any(), any(), any(), any()) }
    }

    @Test
    fun rethrowsCancellationException() = runTest {
        val receiptId = PurchaseReceiptId("r1")
        coEvery { repository.getReceipt(receiptId) } throws kotlinx.coroutines.CancellationException("Cancelled")

        try {
            useCase(receiptId).toList()
            assertWithMessage("Should have thrown CancellationException").fail()
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertThat(e.message).isEqualTo("Cancelled")
        }
    }

    @Test
    fun detectsStaleDocumentRaceDuringSave() = runTest {
        val receiptId = PurchaseReceiptId("r1")
        val path = "path/A"
        val receipt = createReceipt(receiptId, path)
        coEvery { repository.getReceipt(receiptId) } returns receipt
        coEvery { documentStore.inspect(path) } returns StoredPurchaseDocument(path, "A.jpg", "image/jpeg", 100)
        coEvery { documentStore.open(path) } coAnswers { ByteArrayInputStream(ByteArray(10)) }
        coEvery { documentStore.getFile(path) } returns java.io.File(path)

        val bitmap = mockk<Bitmap>(relaxed = true)
        coEvery { SafeImageDecoder.decode(any(), any()) } returns bitmap
        
        // Mock saveOcrResult to fail with "Document changed"
        coEvery { 
            repository.saveOcrResult(any(), any(), path, any()) 
        } throws IllegalStateException("Document changed")

        val results = useCase(receiptId).toList()

        assertThat(results).contains(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.DocumentChanged))
    }

    private fun createReceipt(id: PurchaseReceiptId, path: String?) = PurchaseReceipt(
        id = id,
        restaurantId = RestaurantId("res1"),
        purchaseDate = Instant.now(),
        status = DocumentStatus.DRAFT,
        attachmentPath = path,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
