package com.venkoi.restaurantops.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertWithMessage
import com.venkoi.restaurantops.core.common.ids.PurchaseLineId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDraftViewModelAdmissionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<PurchaseRepository>(relaxed = true)
    private val restaurantRepository = mockk<RestaurantRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns MutableStateFlow(
            Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canStartMutation admission table`() {
        val testCases = listOf(
            TestCase(description = "Fully Idle", expected = true),
            TestCase(description = "Capture Busy", captureState = InvoiceCaptureState.PreparingScanner, expected = false),
            TestCase(description = "OCR Analyzing", ocrState = OcrAnalysisState.Analyzing(1, 2), expected = false),
            TestCase(description = "OCR Parsing", ocrState = OcrAnalysisState.Parsing, expected = false),
            TestCase(description = "OCR Parsed", ocrState = OcrAnalysisState.Parsed, expected = true),
            TestCase(description = "OCR Failure", ocrState = OcrAnalysisState.Failure(mockk()), expected = true),
            TestCase(description = "Scanner Pending", scannerPending = true, expected = false),
            TestCase(description = "Saving", isSaving = true, expected = false),
            TestCase(description = "Posting", isPosting = true, expected = false),
            TestCase(description = "Deleting Draft", isDeletingDraft = true, expected = false),
            TestCase(description = "Removing Document", isRemovingDocument = true, expected = false),
            TestCase(description = "Deleting Line", deletingLineId = PurchaseLineId("l1"), expected = false)
        )

        testCases.forEach { tc ->
            val savedStateHandle = SavedStateHandle(mutableMapOf("receiptId" to "p1"))
            if (tc.scannerPending) {
                savedStateHandle["pendingInvoiceScanSessionId"] = "session1"
            }
            val viewModel = createViewModel(savedStateHandle)
            
            // Inject states via reflection
            viewModel.setInternalState("_captureState", tc.captureState)
            viewModel.setInternalState("_ocrState", tc.ocrState)
            viewModel.setInternalState("_isSaving", tc.isSaving)
            viewModel.setInternalState("_isPosting", tc.isPosting)
            viewModel.setInternalState("_isDeletingDraft", tc.isDeletingDraft)
            viewModel.setInternalState("_isRemovingDocument", tc.isRemovingDocument)
            viewModel.setInternalState("_deletingLineId", tc.deletingLineId)

            val result = viewModel.canStartMutation()
            assertWithMessage(tc.description).that(result).isEqualTo(tc.expected)
        }
    }

    private data class TestCase(
        val description: String,
        val captureState: InvoiceCaptureState = InvoiceCaptureState.Idle,
        val ocrState: OcrAnalysisState = OcrAnalysisState.Idle,
        val scannerPending: Boolean = false,
        val isSaving: Boolean = false,
        val isPosting: Boolean = false,
        val isDeletingDraft: Boolean = false,
        val isRemovingDocument: Boolean = false,
        val deletingLineId: PurchaseLineId? = null,
        val expected: Boolean
    )

    private fun Any.setInternalState(fieldName: String, value: Any?) {
        val field = this.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val stateFlow = field.get(this) as MutableStateFlow<Any?>
        stateFlow.value = value
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle
    ): PurchaseDraftViewModel {
        return PurchaseDraftViewModel(
            savedStateHandle = savedStateHandle,
            createPurchaseDraftUseCase = mockk(relaxed = true),
            updatePurchaseDraftUseCase = mockk(relaxed = true),
            deletePurchaseDraftUseCase = mockk(relaxed = true),
            postPurchaseUseCase = mockk(relaxed = true),
            deletePurchaseLineUseCase = mockk(relaxed = true),
            observePurchaseDetailsUseCase = mockk(relaxed = true),
            observeSuppliersUseCase = mockk(relaxed = true),
            attachPurchaseDocumentUseCase = mockk(relaxed = true),
            removePurchaseDocumentUseCase = mockk(relaxed = true),
            analyzePurchaseInvoiceDocumentUseCase = mockk(relaxed = true),
            parseUseCase = mockk(relaxed = true),
            repository = repository,
            documentStore = mockk(relaxed = true),
            restaurantRepository = restaurantRepository,
            invoiceScanner = mockk(relaxed = true)
        )
    }
}
