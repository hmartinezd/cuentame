package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.repository.ActiveRestaurantProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.ocr.parser.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewDetectedInvoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val repository = mockk<PurchaseRepository>(relaxed = true)
    private val supplierRepository = mockk<SupplierRepository>(relaxed = true)
    private val mappingRepository = mockk<SupplierItemMappingRepository>(relaxed = true)
    private val ingredientRepository = mockk<IngredientRepository>(relaxed = true)
    private val areaRepository = mockk<InventoryAreaRepository>(relaxed = true)
    private val activeRestaurantProvider = mockk<ActiveRestaurantProvider>(relaxed = true)
    private val idGenerator = mockk<IdGenerator>(relaxed = true)
    private val generateProposalUseCase = mockk<GenerateInvoiceProposalUseCase>(relaxed = true)
    private val applyInvoiceUseCase = mockk<ApplyInvoiceToPurchaseDraftUseCase>(relaxed = true)

    private val receiptId = PurchaseReceiptId("r1")
    private val restaurantId = RestaurantId("rest1")
    private val activeRestaurant = RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { activeRestaurantProvider.observeActiveRestaurant() } returns flowOf(activeRestaurant)
        coEvery { activeRestaurantProvider.getActiveRestaurant() } returns activeRestaurant
        
        every { repository.observePurchase(receiptId) } returns flowOf(null)
        every { repository.observeParseResult(receiptId) } returns flowOf(null)
        every { repository.observeLineMatchesForReceipt(receiptId) } returns flowOf(emptyList())
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { areaRepository.observeActiveAreas() } returns flowOf(emptyList())
        every { supplierRepository.observeSuppliers(any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReviewDetectedInvoiceViewModel {
        return ReviewDetectedInvoiceViewModel(
            SavedStateHandle(mapOf("receiptId" to receiptId.value)),
            repository,
            supplierRepository,
            mappingRepository,
            ingredientRepository,
            areaRepository,
            activeRestaurantProvider,
            idGenerator,
            generateProposalUseCase,
            applyInvoiceUseCase
        )
    }

    private fun createEmptyParseResult(id: String) = PurchaseInvoiceParseResult(
        id = id,
        supplierNameCandidate = ParsedField("", "", 1f),
        invoiceNumber = ParsedField("", "", 1f),
        invoiceDate = ParsedField(null, null, 1f),
        subtotal = ParsedField(null, null, 1f),
        discount = ParsedField(null, null, 1f),
        fees = ParsedField(null, null, 1f),
        tax = ParsedField(null, null, 1f),
        total = ParsedField(null, null, 1f),
        currency = ParsedField("USD", "USD", 1f),
        lines = listOf(
            ParsedInvoiceLineCandidate(
                index = 0,
                vendorCode = ParsedField("", "", 1f),
                description = ParsedField("Product 1", "Product 1", 1f),
                quantity = ParsedField("1", BigDecimal.ONE, 1f),
                packageText = ParsedField("", "", 1f),
                unitPrice = ParsedField("10", BigDecimal.TEN, 1f),
                lineTotal = ParsedField("10", BigDecimal.TEN, 1f),
                confidence = 1.0f
            )
        ),
        confidence = 1.0f
    )

    @Test
    fun `confirm match success clears loading and dialog`() = runTest {
        val viewModel = createViewModel()
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } returns LearnMappingResult.Learned
        
        viewModel.onStartMatch(0)
        runCurrent()
        
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        
        viewModel.uiState.test {
            // Confirm started
            assertThat(awaitItem().isConfirmingMatch).isTrue()
            
            // Success state
            val successState = awaitItem()
            assertThat(successState.isConfirmingMatch).isFalse()
            assertThat(successState.matchingLineIndex).isNull()
            assertThat(successState.confirmMatchError).isNull()
        }
    }

    @Test
    fun `confirm match failure surfaces error and keeps dialog open`() = runTest {
        val viewModel = createViewModel()
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } throws com.miara.cuentame.core.domain.validation.ValidationError.ParseResultChanged
        
        viewModel.onStartMatch(0)
        runCurrent()
        
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        
        viewModel.uiState.test {
            // Confirm started
            assertThat(awaitItem().isConfirmingMatch).isTrue()
            
            // Error surfaced
            val errorState = awaitItem()
            assertThat(errorState.isConfirmingMatch).isFalse()
            assertThat(errorState.matchingLineIndex).isEqualTo(0)
            assertThat(errorState.confirmMatchError).isEqualTo(MatchConfirmationError.SourceChanged)
        }
    }

    @Test
    fun `confirm match double tap prevented`() = runTest {
        val viewModel = createViewModel()
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            LearnMappingResult.Learned
        }
        
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        
        advanceTimeBy(150)
        runCurrent()
        
        coVerify(exactly = 1) { 
            repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) 
        }
    }
}
