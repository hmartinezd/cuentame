package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.presentation.ui.UiMessage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchPostingPreviewViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    
    private val testDispatcher = UnconfinedTestDispatcher()

    private val restaurantId = RestaurantId("res1")
    private val batchId = ProductionBatchId("batch1")
    private val ingredientId = IngredientId("ing1")
    private val unitOptionId = IngredientUnitOptionId("unit1")
    private val areaId = InventoryAreaId("area1")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(
            id = restaurantId,
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

    private fun createViewModel(id: String? = "batch1"): ProductionBatchPostingPreviewViewModel {
        return ProductionBatchPostingPreviewViewModel(
            productionBatchRepository,
            ingredientRepository,
            restaurantRepository,
            SavedStateHandle(if (id != null) mapOf("batchId" to id) else emptyMap())
        )
    }

    private fun createBatch(id: ProductionBatchId = batchId) = ProductionBatch(
        id = id,
        restaurantId = restaurantId,
        recipeId = PreparationRecipeId("recipe1"),
        recipeNameSnapshot = "Recipe",
        outputIngredientId = ingredientId,
        batchMultiplier = BigDecimal.ONE,
        recipeStandardYieldQuantitySnapshot = BigDecimal.TEN,
        recipeStandardYieldBaseSnapshot = BigDecimal.TEN,
        recipeYieldUnitOptionIdSnapshot = unitOptionId,
        expectedOutputQuantityEntered = BigDecimal.TEN,
        expectedOutputQuantityBase = BigDecimal.TEN,
        actualOutputQuantityEntered = BigDecimal.TEN,
        actualOutputQuantityBase = BigDecimal.TEN,
        outputUnitOptionId = unitOptionId,
        outputAreaId = areaId,
        hasManualOutputQuantityOverride = false,
        totalComponentCostSnapshot = BigDecimal("100"),
        outputUnitCostBaseSnapshot = BigDecimal.TEN,
        effectiveAt = Instant.EPOCH,
        status = DocumentStatus.DRAFT,
        notes = null,
        components = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        postedAt = null,
        voidedAt = null
    )

    private fun createPreview(
        id: ProductionBatchId = batchId,
        blockers: List<PostingBlocker> = emptyList(),
        components: List<ProductionBatchComponentPostingPreview> = emptyList()
    ) = ProductionBatchPostingPreview(
        batchId = id,
        effectiveAt = Instant.EPOCH,
        components = components,
        totalComponentCost = BigDecimal("100"),
        actualOutputQuantityBase = BigDecimal.TEN,
        outputUnitCostBase = BigDecimal.TEN,
        yieldVariancePercent = BigDecimal.ZERO,
        blockers = blockers
    )

    private fun createComponentPreview() = ProductionBatchComponentPostingPreview(
        componentId = ProductionBatchComponentId("comp1"),
        ingredientId = IngredientId("ing2"),
        ingredientName = "Component",
        sourceAreaId = InventoryAreaId("area2"),
        sourceAreaName = "Area 2",
        actualQuantityEntered = BigDecimal.ONE,
        actualQuantityBase = BigDecimal.ONE,
        unitOptionLabel = "Kg",
        currentAreaBalanceBase = BigDecimal.TEN,
        remainingAreaBalanceBase = BigDecimal("9"),
        createsNegativeBalance = false,
        averageUnitCostBase = BigDecimal.TEN,
        totalCost = BigDecimal.TEN,
        costUnavailable = false
    )

    @Test
    fun `preview calculation success`() = runTest {
        val batch = createBatch()
        val preview = createPreview()
        val unitOption = mockk<IngredientUnitOption> {
            every { id } returns unitOptionId
            every { displayName } returns "Kg"
        }

        coEvery { productionBatchRepository.getBatch(any()) } returns batch
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns preview
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unitOption)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals(preview, state.preview)
        assertEquals("USD", state.currencyCode)
        assertEquals("Kg", state.outputUnitLabel)
    }

    @Test
    fun `detects blockers and warnings`() = runTest {
        val batch = createBatch()
        val preview = createPreview(
            blockers = listOf(PostingBlocker.RECIPE_NOT_ACTIVE),
            components = listOf(createComponentPreview().copy(createsNegativeBalance = true, costUnavailable = true))
        )

        coEvery { productionBatchRepository.getBatch(any()) } returns batch
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns preview
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.blockers.isNotEmpty())
        assertTrue(state.hasNegativeBalances)
        assertTrue(state.hasUnavailableCosts)
    }

    @Test
    fun `invalid route when batchId is missing`() = runTest {
        val viewModel = createViewModel(id = null)
        assertEquals(ProductionBatchScreenState.InvalidRoute, viewModel.uiState.value.screenState)
    }

    @Test
    fun `batch not found state`() = runTest {
        coEvery { productionBatchRepository.getBatch(any()) } returns null
        val viewModel = createViewModel()
        assertEquals(ProductionBatchScreenState.BatchNotFound, viewModel.uiState.value.screenState)
    }

    @Test
    fun `onPost does nothing when blockers are present`() = runTest {
        val preview = createPreview(blockers = listOf(PostingBlocker.RECIPE_NOT_ACTIVE))
        coEvery { productionBatchRepository.getBatch(any()) } returns createBatch()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns preview
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()

        val viewModel = createViewModel()

        viewModel.onPost()

        coVerify(exactly = 0) { productionBatchRepository.post(any()) }
        assertFalse(viewModel.uiState.value.isPosting)
    }

    @Test
    fun `duplicate post call ignored while posting`() = runTest {
        coEvery { productionBatchRepository.getBatch(any()) } returns createBatch()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns createPreview()
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        
        val postCompletable = CompletableDeferred<Unit>()
        coEvery { productionBatchRepository.post(any()) } coAnswers {
            postCompletable.await()
        }

        val viewModel = createViewModel()

        launch { viewModel.onPost() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isPosting)
        
        viewModel.onPost()
        
        postCompletable.complete(Unit)
        runCurrent()
        
        coVerify(exactly = 1) { productionBatchRepository.post(any()) }
    }

    @Test
    fun `posting success emits event`() = runTest {
        coEvery { productionBatchRepository.getBatch(any()) } returns createBatch()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns createPreview()
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { productionBatchRepository.post(any()) } returns Unit

        val events = mutableListOf<ProductionBatchPreviewEvent>()
        val viewModel = createViewModel()
        val job = launch { viewModel.events.collect { events.add(it) } }
        
        viewModel.onPost()
        runCurrent()
        advanceUntilIdle()
        
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchPreviewEvent.Posted)
        
        job.cancel()
    }

    @Test
    fun `rethrows CancellationException on post`() = runTest {
        coEvery { productionBatchRepository.getBatch(any()) } returns createBatch()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns createPreview()
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { productionBatchRepository.post(any()) } throws CancellationException()

        val viewModel = createViewModel()
        viewModel.onPost()
        runCurrent()
        // Should not crash and isPosting should be reset by finally block
        assertFalse(viewModel.uiState.value.isPosting)
    }
}
