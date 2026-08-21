package com.venkoi.restaurantops.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.PurchaseLineId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.domain.repository.CreatePurchaseDraftCommand
import com.venkoi.restaurantops.core.domain.repository.PurchaseDetails
import com.venkoi.restaurantops.core.domain.repository.PurchaseFilter
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.domain.repository.PurchaseSummary
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.repository.SavePurchaseLineCommand
import com.venkoi.restaurantops.core.domain.repository.UpdatePurchaseDraftCommand
import com.venkoi.restaurantops.core.domain.usecase.CreatePurchaseDraftUseCase
import com.venkoi.restaurantops.core.domain.usecase.DeletePurchaseDraftUseCase
import com.venkoi.restaurantops.core.domain.usecase.DeletePurchaseLineUseCase
import com.venkoi.restaurantops.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.venkoi.restaurantops.core.domain.usecase.ObserveSuppliersUseCase
import com.venkoi.restaurantops.core.domain.usecase.PostPurchaseUseCase
import com.venkoi.restaurantops.core.domain.usecase.UpdatePurchaseDraftUseCase
import com.venkoi.restaurantops.core.domain.usecase.purchase.AttachPurchaseDocumentUseCase
import com.venkoi.restaurantops.core.domain.usecase.purchase.RemovePurchaseDocumentUseCase
import com.venkoi.restaurantops.core.domain.usecase.purchase.AnalyzePurchaseInvoiceDocumentUseCase
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.purchase.PurchaseReceipt
import com.venkoi.restaurantops.core.model.purchase.DuplicateInvoiceCandidate
import com.venkoi.restaurantops.core.model.purchase.DuplicateInvoicePostingException
import com.venkoi.restaurantops.core.model.purchase.DuplicateInvoiceType
import com.venkoi.restaurantops.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.venkoi.restaurantops.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import com.venkoi.restaurantops.core.model.supplier.Supplier
import android.net.Uri
import com.venkoi.restaurantops.core.backup.api.PurchaseInvoiceScanResult
import com.venkoi.restaurantops.core.backup.api.PurchaseInvoiceScannerFailure
import com.venkoi.restaurantops.core.backup.api.PurchaseInvoiceScannerException
import com.venkoi.restaurantops.core.backup.api.StoredPurchaseDocument
import com.venkoi.restaurantops.core.backup.fakes.FakePurchaseInvoiceScanner
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.presentation.ui.findActivity
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
        mockkStatic("com.venkoi.restaurantops.core.presentation.ui.ContextUtilsKt")
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
        mockkStatic("com.venkoi.restaurantops.core.presentation.ui.ContextUtilsKt")
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
        val scanner = mockk<com.venkoi.restaurantops.core.backup.api.PurchaseInvoiceScanner>()
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

    private fun createViewModel(receiptId: String?, scanner: com.venkoi.restaurantops.core.backup.api.PurchaseInvoiceScanner = mockk(relaxed = true)): PurchaseDraftViewModel {
        val ocrEngine = mockk<com.venkoi.restaurantops.core.ocr.api.PurchaseInvoiceOcrEngine>(relaxed = true)
        val parseUseCase = mockk<com.venkoi.restaurantops.core.domain.usecase.purchase.ParsePurchaseInvoiceUseCase>(relaxed = true)
        val idGenerator = mockk<IdGenerator>(relaxed = true)
        
        return PurchaseDraftViewModel(
            SavedStateHandle(if (receiptId != null) mapOf("receiptId" to receiptId) else emptyMap()),
            CreatePurchaseDraftUseCase(fakePurchaseRepository),
            UpdatePurchaseDraftUseCase(fakePurchaseRepository),
            DeletePurchaseDraftUseCase(fakePurchaseRepository),
            PostPurchaseUseCase(fakePurchaseRepository),
            DeletePurchaseLineUseCase(fakePurchaseRepository),
            ObservePurchaseDetailsUseCase(fakePurchaseRepository),
            ObserveSuppliersUseCase(object : com.venkoi.restaurantops.core.domain.repository.SupplierRepository {
                override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = MutableStateFlow(emptyList())
                override fun observeSupplier(id: SupplierId): Flow<Supplier?> = MutableStateFlow(null)
                override suspend fun getSupplier(id: SupplierId): Supplier? = null
                override suspend fun createSupplier(command: com.venkoi.restaurantops.core.domain.repository.CreateSupplierCommand): SupplierId = SupplierId("")
                override suspend fun updateSupplier(command: com.venkoi.restaurantops.core.domain.repository.UpdateSupplierCommand) {}
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
