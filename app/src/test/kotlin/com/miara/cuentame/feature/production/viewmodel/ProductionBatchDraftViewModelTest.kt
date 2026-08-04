package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class ProductionBatchDraftViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val restaurantId = RestaurantId("res1")
    private val batchId = ProductionBatchId("batch1")
    private val areaId = InventoryAreaId("area1")
    private val ingredientId = IngredientId("ing1")
    private val unit1Id = IngredientUnitOptionId("unit1")
    private val unit2Id = IngredientUnitOptionId("unit2")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(
            id = restaurantId,
            name = "Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        every { timeProvider.now() } returns Instant.EPOCH
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(id: String = batchId.value): ProductionBatchDraftViewModel {
        return ProductionBatchDraftViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            timeProvider,
            SavedStateHandle(mapOf("batchId" to id))
        )
    }

    private fun createBatch(
        id: String = batchId.value,
        status: DocumentStatus = DocumentStatus.DRAFT,
        components: List<com.miara.cuentame.core.model.inventory.ProductionBatchComponent> = emptyList()
    ) = ProductionBatch(
        id = ProductionBatchId(id),
        restaurantId = restaurantId,
        recipeId = PreparationRecipeId("rec1"),
        recipeNameSnapshot = "Recipe",
        outputIngredientId = ingredientId,
        batchMultiplier = BigDecimal.ONE,
        recipeStandardYieldQuantitySnapshot = BigDecimal("10"),
        recipeStandardYieldBaseSnapshot = BigDecimal("10"),
        recipeYieldUnitOptionIdSnapshot = unit1Id,
        expectedOutputQuantityEntered = BigDecimal("10"),
        expectedOutputQuantityBase = BigDecimal("10"),
        actualOutputQuantityEntered = BigDecimal("10"),
        actualOutputQuantityBase = BigDecimal("10"),
        outputUnitOptionId = unit1Id,
        outputAreaId = areaId,
        hasManualOutputQuantityOverride = false,
        totalComponentCostSnapshot = null,
        outputUnitCostBaseSnapshot = null,
        effectiveAt = Instant.EPOCH,
        status = status,
        notes = null,
        components = components,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        postedAt = null,
        voidedAt = null
    )

    private fun createUnitOption(id: IngredientUnitOptionId, factor: BigDecimal) = IngredientUnitOption(
        id = id,
        ingredientId = ingredientId,
        displayName = "Unit $id",
        shortLabel = "u",
        standardUnitId = null,
        factorToBase = factor,
        isBase = factor == BigDecimal.ONE,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createArea(id: InventoryAreaId, name: String) = InventoryArea(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = name.lowercase(),
        sortOrder = 0,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private val batchFlow = MutableStateFlow<ProductionBatch?>(null)

    private fun setupDefaultStubs(batch: ProductionBatch = createBatch()) {
        batchFlow.value = batch
        every { productionBatchRepository.observeBatch(any()) } returns batchFlow
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(createArea(areaId, "Area 1")))
        coEvery { inventoryAreaRepository.getById(any()) } returns createArea(areaId, "Area")
        coEvery { ingredientRepository.getById(any()) } returns mockk<Ingredient> { 
            every { id } returns ingredientId
            every { name } returns "Ingredient" 
        }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(
            createUnitOption(unit1Id, BigDecimal.ONE),
            createUnitOption(unit2Id, BigDecimal("2"))
        )
    }

    @Test
    fun `invalid route state verified`() = runTest {
        val viewModel = createViewModel("")
        assertEquals(ProductionBatchScreenState.InvalidRoute, viewModel.uiState.value.screenState)
    }

    @Test
    fun `batch not found state verified`() = runTest {
        val flow = MutableStateFlow<ProductionBatch?>(null)
        every { productionBatchRepository.observeBatch(any()) } returns flow
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()
        assertEquals(ProductionBatchScreenState.BatchNotFound, viewModel.uiState.value.screenState)
    }

    @Test
    fun `effective time change generates focused command`() = runTest {
        setupDefaultStubs()
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()
        
        val newTime = Instant.EPOCH.minusSeconds(3600)

        viewModel.onEffectiveAtChanged(newTime)
        coEvery { productionBatchRepository.updateDraft(any()) } returns Unit
        
        viewModel.onSave()
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 1) { productionBatchRepository.updateDraft(any()) }
    }

    @Test
    fun `unsaved changes guard on review button`() = runTest {
        setupDefaultStubs()
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.onNotesChanged("Dirty")
        viewModel.onReview()
        
        coEvery { productionBatchRepository.updateDraft(any()) } returns Unit
        viewModel.onSave()
        batchFlow.value = createBatch().copy(notes = "Dirty") // Simulate update
        runCurrent()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onReview()
        val event = viewModel.events.first()
        assertTrue(event is ProductionBatchDraftEvent.NavigateToPreview)
    }

    @Test
    fun `serialization of operations - isSaving prevents onSave`() = runTest {
        setupDefaultStubs()
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()
        
        viewModel.onNotesChanged("Test")

        coEvery { productionBatchRepository.updateDraft(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            Unit
        }

        val saveJob = launch { viewModel.onSave() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.onSave() // Should be ignored

        advanceTimeBy(1001)
        coVerify(exactly = 1) { productionBatchRepository.updateDraft(any()) }
        assertFalse(viewModel.uiState.value.isSaving)
        saveJob.cancel()
    }

    @Test
    fun `delete resets isDeleting on success and failure`() = runTest {
        setupDefaultStubs()
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // Success
        coEvery { productionBatchRepository.deleteDraft(any()) } returns Unit
        
        viewModel.onDelete()
        val event = viewModel.events.first()
        assertTrue(event is ProductionBatchDraftEvent.Deleted)
        assertFalse(viewModel.uiState.value.isDeleting)

        // Failure
        coEvery { productionBatchRepository.deleteDraft(any()) } throws Exception("boom")
        viewModel.onDelete()
        assertFalse(viewModel.uiState.value.isDeleting)
        assertNotNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun `save and delete operations are mutually exclusive`() = runTest {
        setupDefaultStubs()
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()
        
        viewModel.onNotesChanged("Dirty")

        coEvery { productionBatchRepository.updateDraft(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            Unit
        }

        launch { viewModel.onSave() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.onDelete() // Should be ignored
        coVerify(exactly = 0) { productionBatchRepository.deleteDraft(any()) }
        
        advanceTimeBy(1001)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
