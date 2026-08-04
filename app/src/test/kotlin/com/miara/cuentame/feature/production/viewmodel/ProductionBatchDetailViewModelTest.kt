package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.presentation.ui.UiMessage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchDetailViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(
            id = RestaurantId("res1"),
            name = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(batchId: String? = "batch1"): ProductionBatchDetailViewModel {
        return ProductionBatchDetailViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            SavedStateHandle(if (batchId != null) mapOf("batchId" to batchId) else emptyMap())
        )
    }

    private fun createBatch(
        id: String = "batch1",
        status: DocumentStatus = DocumentStatus.POSTED
    ) = ProductionBatch(
        id = ProductionBatchId(id),
        restaurantId = RestaurantId("res1"),
        recipeId = PreparationRecipeId("recipe1"),
        recipeNameSnapshot = "Test Recipe",
        outputIngredientId = IngredientId("ing1"),
        batchMultiplier = BigDecimal.ONE,
        recipeStandardYieldQuantitySnapshot = BigDecimal.TEN,
        recipeStandardYieldBaseSnapshot = BigDecimal.TEN,
        recipeYieldUnitOptionIdSnapshot = IngredientUnitOptionId("unit1"),
        expectedOutputQuantityEntered = BigDecimal.TEN,
        expectedOutputQuantityBase = BigDecimal.TEN,
        actualOutputQuantityEntered = BigDecimal.TEN,
        actualOutputQuantityBase = BigDecimal.TEN,
        outputUnitOptionId = IngredientUnitOptionId("unit1"),
        outputAreaId = InventoryAreaId("area1"),
        hasManualOutputQuantityOverride = false,
        totalComponentCostSnapshot = BigDecimal("100"),
        outputUnitCostBaseSnapshot = BigDecimal("10"),
        effectiveAt = Instant.EPOCH,
        status = status,
        notes = null,
        components = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        postedAt = Instant.EPOCH,
        voidedAt = null
    )

    private fun stubSuccessDependencies() {
        coEvery { ingredientRepository.getById(any()) } returns mockk { 
            every { id } returns IngredientId("ing1")
            every { name } returns "Output Ing" 
        }
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { 
            every { id } returns InventoryAreaId("area1")
            every { name } returns "Output Area" 
        }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(
            mockk { 
                every { id } returns IngredientUnitOptionId("unit1")
                every { displayName } returns "Output Unit" 
            }
        )
    }

    @Test
    fun `data enrichment and initialization success`() = runTest {
        stubSuccessDependencies()
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals("Output Ing", state.outputIngredientName)
        assertEquals("Output Area", state.outputAreaName)
        assertEquals("Output Unit", state.outputUnitLabel)
        assertEquals("USD", state.currencyCode)
    }

    @Test
    fun `invalid route with null batchId`() = runTest {
        val viewModel = createViewModel(batchId = null)
        assertEquals(ProductionBatchScreenState.InvalidRoute, viewModel.uiState.value.screenState)
    }

    @Test
    fun `batch not found`() = runTest {
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(null)
        val viewModel = createViewModel()
        assertEquals(ProductionBatchScreenState.BatchNotFound, viewModel.uiState.value.screenState)
    }

    @Test
    fun `navigation to draft redirect exactly once`() = runTest {
        val batchFlow = MutableSharedFlow<ProductionBatch?>(replay = 1)
        every { productionBatchRepository.observeBatch(any()) } returns batchFlow

        val events = mutableListOf<ProductionBatchDetailEvent>()
        val viewModel = createViewModel()
        val job = launch { viewModel.events.collect { events.add(it) } }

        val draftBatch = createBatch(status = DocumentStatus.DRAFT)
        batchFlow.emit(draftBatch)
        runCurrent()
        advanceUntilIdle()
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchDetailEvent.NavigateToDraft)

        batchFlow.emit(draftBatch)
        assertEquals(1, events.size)

        job.cancel()
    }

    @Test
    fun `strict enrichment failure - ingredient null`() = runTest {
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())
        coEvery { ingredientRepository.getById(any()) } returns null

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.screenState is ProductionBatchScreenState.LoadError)
    }

    @Test
    fun `retry success`() = runTest {
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())
        coEvery { ingredientRepository.getById(any()) } returns null // Initial failure

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.screenState is ProductionBatchScreenState.LoadError)

        stubSuccessDependencies()
        viewModel.onRetry()

        assertEquals(ProductionBatchScreenState.Ready, viewModel.uiState.value.screenState)
    }

    @Test
    fun `duplicate void call is ignored while operating`() = runTest {
        stubSuccessDependencies()
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())
        coEvery { productionBatchRepository.void(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
        }

        val viewModel = createViewModel()

        val voidJob = launch { viewModel.onVoid() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isOperating)

        viewModel.onVoid() // Should be ignored
        
        voidJob.join()
        coVerify(exactly = 1) { productionBatchRepository.void(any()) }
    }

    @Test
    fun `void success`() = runTest {
        stubSuccessDependencies()
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())
        coEvery { productionBatchRepository.void(any()) } returns Unit
        
        val viewModel = createViewModel()
        viewModel.onVoid()
        
        coVerify { productionBatchRepository.void(any()) }
        assertFalse(viewModel.uiState.value.isOperating)
    }

    @Test
    fun `resets isOperating on CancellationException`() = runTest {
        stubSuccessDependencies()
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(createBatch())
        coEvery { productionBatchRepository.void(any()) } throws CancellationException()

        val viewModel = createViewModel()
        viewModel.onVoid()
        
        assertFalse(viewModel.uiState.value.isOperating)
        assertNull(viewModel.uiState.value.inlineError)
    }
}
