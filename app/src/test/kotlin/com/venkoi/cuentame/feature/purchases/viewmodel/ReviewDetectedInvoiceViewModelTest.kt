package com.venkoi.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import com.venkoi.cuentame.core.database.repository.ActiveRestaurantProvider
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.venkoi.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.venkoi.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.venkoi.cuentame.core.model.supplier.Supplier
import com.venkoi.cuentame.core.ocr.parser.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    private fun createViewModel(initialState: Map<String, Any?> = mapOf("receiptId" to receiptId.value)): ReviewDetectedInvoiceViewModel {
        return ReviewDetectedInvoiceViewModel(
            SavedStateHandle(initialState),
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
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(50)
            LearnMappingResult.Learned
        }
        
        viewModel.onStartMatch(0)
        advanceUntilIdle()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().matchingLineIndex).isEqualTo(0)
            
            viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
            
            // Advance to the point where isConfirmingMatch is set
            runCurrent()
            assertThat(expectMostRecentItem().isConfirmingMatch).isTrue()
            
            // Advance to completion
            advanceTimeBy(100)
            runCurrent()
            val finalState = expectMostRecentItem()
            assertThat(finalState.isConfirmingMatch).isFalse()
            assertThat(finalState.matchingLineIndex).isNull()
            assertThat(finalState.confirmMatchError).isNull()
        }
    }

    @Test
    fun `confirm match failure surfaces error and keeps dialog open`() = runTest {
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(50)
            throw com.venkoi.cuentame.core.domain.validation.ValidationError.ParseResultChanged
        }
        
        viewModel.onStartMatch(0)
        advanceUntilIdle()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().matchingLineIndex).isEqualTo(0)
            
            viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
            
            // Confirm started
            runCurrent()
            assertThat(expectMostRecentItem().isConfirmingMatch).isTrue()
            
            // Error surfaced
            advanceTimeBy(100)
            runCurrent()
            val errorState = expectMostRecentItem()
            assertThat(errorState.isConfirmingMatch).isFalse()
            assertThat(errorState.matchingLineIndex).isEqualTo(0)
            assertThat(errorState.confirmMatchError).isEqualTo(MatchConfirmationError.SourceChanged)
        }
    }

    @Test
    fun `confirm match double call prevents multiple repository calls`() = runTest {
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(100)
            LearnMappingResult.Learned
        }
        
        // Trigger both calls immediately
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
        
        advanceTimeBy(200)
        runCurrent()
        
        coVerify(exactly = 1) { 
            repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) 
        }
    }

    @Test
    fun `match selection validation logic`() {
        val ingredientId = IngredientId("i1")
        val unitOptionId = IngredientUnitOptionId("u1")
        val areaId = InventoryAreaId("a1")
        val unitOptions = listOf(
            IngredientUnitOption(
                id = unitOptionId,
                ingredientId = ingredientId,
                displayName = "Unit 1",
                shortLabel = "U1",
                standardUnitId = null,
                factorToBase = BigDecimal.ONE,
                isBase = true,
                isDefaultCount = true,
                isDefaultPurchase = true,
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        
        // Valid
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, unitOptionId, areaId, unitOptions)).isTrue()
        
        // Missing ingredient
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(null, unitOptionId, areaId, unitOptions)).isFalse()
        
        // Missing unit option
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, null, areaId, unitOptions)).isFalse()
        
        // Missing area
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, unitOptionId, null, unitOptions)).isFalse()
        
        // Stale unit option (not in list)
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, IngredientUnitOptionId("u2"), areaId, unitOptions)).isFalse()
        
        // Empty options list
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, unitOptionId, areaId, emptyList())).isFalse()
        
        // Null options list
        assertThat(ReviewDetectedInvoiceViewModel.isMatchSelectionValid(ingredientId, unitOptionId, areaId, null)).isFalse()
    }

    @Test
    fun `confirm match mapping conflict transitions to conflict ui and clears matching context`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("receiptId" to receiptId.value))
        val parseResult = createEmptyParseResult("p1")
        every { repository.observeParseResult(receiptId) } returns flowOf(parseResult)
        
        val viewModel = ReviewDetectedInvoiceViewModel(
            savedStateHandle,
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
        advanceUntilIdle()
        
        val conflict = mockk<MappingConflict>()
        coEvery { repository.confirmInvoiceLineMatch(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(50)
            LearnMappingResult.Conflict(conflict)
        }
        
        viewModel.onStartMatch(0)
        advanceUntilIdle()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().matchingLineIndex).isEqualTo(0)
            
            viewModel.onConfirmMatch(0, IngredientId("i1"), IngredientUnitOptionId("u1"), InventoryAreaId("a1"))
            
            runCurrent()
            assertThat(expectMostRecentItem().isConfirmingMatch).isTrue()
            
            advanceTimeBy(100)
            runCurrent()
            val conflictState = expectMostRecentItem()
            assertThat(conflictState.isConfirmingMatch).isFalse()
            assertThat(conflictState.activeMappingConflict).isEqualTo(conflict)
            assertThat(conflictState.matchingLineIndex).isNull()
            assertThat(savedStateHandle.get<Int>("matchingLineIndex")).isNull()
        }
    }

    @Test
    fun `supplier matching compacts OCR whitespace without rewriting raw evidence`() {
        val chicago = supplier("s1", "Chicago Foods Inc.")
        val different = supplier("s2", "Chicago Food Equipment")
        val raw = "Chi cago Foods Inc"

        val matches = ReviewDetectedInvoiceViewModel.resolveSupplierCandidates(raw, listOf(chicago, different))

        assertThat(matches).containsExactly(chicago)
        assertThat(raw).isEqualTo("Chi cago Foods Inc")
    }

    @Test
    fun `supplier matching tolerates common OCR substitution`() {
        val chicago = supplier("s1", "Chicago Foods Inc.")

        val matches = ReviewDetectedInvoiceViewModel.resolveSupplierCandidates("Chicago Foods lnc.", listOf(chicago))

        assertThat(matches).containsExactly(chicago)
    }

    @Test
    fun `ambiguous supplier near matches remain multiple candidates`() {
        val foods = supplier("s1", "Chicago Foods Inc.")
        val food = supplier("s2", "Chicago Food Inc.")

        val matches = ReviewDetectedInvoiceViewModel.resolveSupplierCandidates("Chicago Foo ds Inc", listOf(foods, food))

        assertThat(matches).containsExactly(foods, food).inOrder()
    }

    private fun supplier(id: String, name: String) = Supplier(
        id = SupplierId(id), restaurantId = restaurantId, name = name,
        normalizedName = name.lowercase(), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )
}
