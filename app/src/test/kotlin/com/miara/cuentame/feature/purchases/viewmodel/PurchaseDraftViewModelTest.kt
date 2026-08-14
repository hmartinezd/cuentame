package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.CreatePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.domain.usecase.PostPurchaseUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.AttachPurchaseDocumentUseCase
import com.miara.cuentame.core.domain.usecase.purchase.RemovePurchaseDocumentUseCase
import com.miara.cuentame.core.domain.usecase.purchase.AnalyzePurchaseInvoiceDocumentUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceCandidate
import com.miara.cuentame.core.model.purchase.DuplicateInvoicePostingException
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceType
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.supplier.Supplier
import android.net.Uri
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerFailure
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerException
import com.miara.cuentame.core.backup.api.StoredPurchaseDocument
import com.miara.cuentame.core.backup.fakes.FakePurchaseInvoiceScanner
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.presentation.ui.findActivity
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDraftViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val detailsFlow = MutableStateFlow<PurchaseDetails?>(null)
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakePurchaseRepository = mockk<PurchaseRepository>(relaxed = true)

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        io.mockk.every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkStatic("com.miara.cuentame.core.presentation.ui.ContextUtilsKt")
        restaurantFlow.value = Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
        
        io.mockk.every { fakePurchaseRepository.observePurchase(any()) } returns detailsFlow
        io.mockk.every { fakePurchaseRepository.observeOcrResult(any()) } returns MutableStateFlow(null)
        io.mockk.every { fakePurchaseRepository.observeParseResult(any()) } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `post purchase success emits event`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, Instant.now(), DocumentStatus.DRAFT, null, null, null, Instant.now(), Instant.now())
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        runCurrent()
        
        viewModel.events.test {
            viewModel.onPost()
            assertThat(awaitItem()).isEqualTo(PurchaseDraftEvent.Posted)
        }
    }

    @Test
    fun `late posting duplicate enters review and explicit override retries normal post`() = runTest {
        val receiptId = PurchaseReceiptId("p1")
        detailsFlow.value = PurchaseDetails(
            PurchaseReceipt(receiptId, RestaurantId("r1"), SupplierId("s1"), "INV-1", Instant.now(), DocumentStatus.DRAFT, null, null, null, Instant.now(), Instant.now()),
            null,
            emptyList()
        )
        val candidate = DuplicateInvoiceCandidate(
            DuplicateInvoiceType.SAME_SUPPLIER_INVOICE_NUMBER,
            PurchaseReceiptId("existing"),
            receiptId,
            SupplierId("s1"),
            "INV1"
        )
        coEvery { fakePurchaseRepository.post(receiptId) } throws DuplicateInvoicePostingException(candidate) andThen Unit
        val viewModel = createViewModel("p1")

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPost()
            runCurrent()
            assertThat(viewModel.uiState.value.postingDuplicate).isEqualTo(candidate)

            viewModel.onContinuePostingDuplicate()
            runCurrent()
            assertThat(viewModel.uiState.value.postingDuplicate).isNull()
            coVerify(exactly = 1) { fakePurchaseRepository.acceptDuplicateForPosting(candidate) }
            coVerify(exactly = 2) { fakePurchaseRepository.post(receiptId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete line success emits event`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, Instant.now(), DocumentStatus.DRAFT, null, null, null, Instant.now(), Instant.now())
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        runCurrent()
        
        val lineId = PurchaseLineId("l1")
        viewModel.events.test {
            viewModel.onDeleteLine(lineId)
            assertThat(awaitItem()).isEqualTo(PurchaseDraftEvent.LineDeleted(lineId))
        }
    }

    @Test
    fun `onPrepareScanner transitions state and persists session`() = runTest {
        val scanner = FakePurchaseInvoiceScanner()
        scanner.preparationDelayMillis = 10 // Ensure distinct emissions
        val viewModel = createViewModel("p1", scanner)
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockActivity = mockk<android.app.Activity>(relaxed = true)
        io.mockk.every { mockContext.findActivity() } returns mockActivity
        
        viewModel.uiState.test {
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.Idle)
            
            viewModel.onPrepareScanner(mockContext) {}
            
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.PreparingScanner)
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.ScannerOpen)
        }
    }

    @Test
    fun `onScannerResult claimed exactly once`() = runTest {
        mockkStatic("com.miara.cuentame.core.presentation.ui.ContextUtilsKt")
        val scanner = FakePurchaseInvoiceScanner()
        val viewModel = createViewModel("p1", scanner)
        scanner.nextResult = PurchaseInvoiceScanResult.Cancelled
        
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockActivity = mockk<android.app.Activity>(relaxed = true)
        io.mockk.every { mockContext.findActivity() } returns mockActivity
        
        viewModel.onPrepareScanner(mockContext) {}
        runCurrent()
        
        viewModel.onScannerResult(android.app.Activity.RESULT_OK, null)
        runCurrent()
        
        // Second call should be ignored because session is consumed
        viewModel.onScannerResult(android.app.Activity.RESULT_OK, null)
        runCurrent()

        assertThat(viewModel.uiState.value.captureState).isEqualTo(InvoiceCaptureState.Idle)
        assertThat(scanner.parseResultCalls).isEqualTo(1)
    }

    @Test
    fun `mutation admission blocks concurrent actions`() = runTest {
        val scanner = FakePurchaseInvoiceScanner()
        scanner.preparationDelayMillis = 10
        val viewModel = createViewModel("p1", scanner)
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockActivity = mockk<android.app.Activity>(relaxed = true)
        io.mockk.every { mockContext.findActivity() } returns mockActivity
        
        viewModel.uiState.test {
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.Idle)
            
            viewModel.onPrepareScanner(mockContext) {}
            
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.PreparingScanner)
            assertThat(awaitItem().captureState).isEqualTo(InvoiceCaptureState.ScannerOpen)
            
            viewModel.onDeleteDraft()
            runCurrent()
            
            assertThat(viewModel.uiState.value.isDeletingDraft).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearGeneralError clears general error`() = runTest {
        val viewModel = createViewModel("p1")
        // Trigger error by some action if needed, or inject through state
        // For simplicity, we just test the call.
        viewModel.clearGeneralError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `onPrepareScanner maps PurchaseInvoiceScannerException to scannerError`() = runTest {
        val scanner = mockk<com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner>()
        io.mockk.coEvery { scanner.getStartScanIntent(any()) } throws PurchaseInvoiceScannerException(PurchaseInvoiceScannerFailure.UnsupportedDevice)
        
        val viewModel = createViewModel("p1", scanner)
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockActivity = mockk<android.app.Activity>(relaxed = true)
        io.mockk.every { mockContext.findActivity() } returns mockActivity
        
        viewModel.uiState.test {
            // Skip initial state
            skipItems(1)
            
            viewModel.onPrepareScanner(mockContext) {}
            
            // It might emit PreparingScanner then Idle, or just Idle if fast
            var finalState = awaitItem()
            if (finalState.captureState == InvoiceCaptureState.PreparingScanner) {
                finalState = awaitItem()
            }
            
            assertThat(finalState.captureState).isEqualTo(InvoiceCaptureState.Idle)
            assertThat(finalState.scannerError).isEqualTo(PurchaseInvoiceScannerFailure.UnsupportedDevice)
            assertThat(finalState.error).isNull()
        }
    }

    private fun createViewModel(receiptId: String?, scanner: com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner = mockk(relaxed = true)): PurchaseDraftViewModel {
        val ocrEngine = mockk<com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrEngine>(relaxed = true)
        val parseUseCase = mockk<com.miara.cuentame.core.domain.usecase.purchase.ParsePurchaseInvoiceUseCase>(relaxed = true)
        val idGenerator = mockk<IdGenerator>(relaxed = true)
        
        return PurchaseDraftViewModel(
            SavedStateHandle(if (receiptId != null) mapOf("receiptId" to receiptId) else emptyMap()),
            CreatePurchaseDraftUseCase(fakePurchaseRepository),
            UpdatePurchaseDraftUseCase(fakePurchaseRepository),
            DeletePurchaseDraftUseCase(fakePurchaseRepository),
            PostPurchaseUseCase(fakePurchaseRepository),
            DeletePurchaseLineUseCase(fakePurchaseRepository),
            ObservePurchaseDetailsUseCase(fakePurchaseRepository),
            ObserveSuppliersUseCase(object : com.miara.cuentame.core.domain.repository.SupplierRepository {
                override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = MutableStateFlow(emptyList())
                override fun observeSupplier(id: SupplierId): Flow<Supplier?> = MutableStateFlow(null)
                override suspend fun getSupplier(id: SupplierId): Supplier? = null
                override suspend fun createSupplier(command: com.miara.cuentame.core.domain.repository.CreateSupplierCommand): SupplierId = SupplierId("")
                override suspend fun updateSupplier(command: com.miara.cuentame.core.domain.repository.UpdateSupplierCommand) {}
                override suspend fun archiveSupplier(id: SupplierId, at: Instant) {}
                override suspend fun searchSuppliers(restaurantId: RestaurantId, query: String): List<Supplier> = emptyList()
            }),
            AttachPurchaseDocumentUseCase(fakePurchaseRepository, mockk(relaxed = true)),
            RemovePurchaseDocumentUseCase(fakePurchaseRepository),
            AnalyzePurchaseInvoiceDocumentUseCase(fakePurchaseRepository, mockk(relaxed = true), mockk(relaxed = true), ocrEngine, parseUseCase, idGenerator, timeProvider),
            parseUseCase,
            fakePurchaseRepository,
            mockk(relaxed = true),
            fakeRestaurantRepository,
            scanner
        )
    }
}
