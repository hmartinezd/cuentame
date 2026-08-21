package com.venkoi.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationException
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.inventory.*
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
class ProductionBatchComponentViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val batchId = ProductionBatchId("batch-123")
    private val componentId = ProductionBatchComponentId("comp-456")
    private val ingredientId = IngredientId("ing-789")
    private val unitOptionId = IngredientUnitOptionId("unit-001")
    private val areaId = InventoryAreaId("area-002")
    private val restaurantId = RestaurantId("res-999")

    @Before
    fun setup() {
        clearMocks(productionBatchRepository, ingredientRepository, inventoryAreaRepository, restaurantRepository)
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

    private fun createBatch(
        status: DocumentStatus = DocumentStatus.DRAFT,
        components: List<ProductionBatchComponent> = emptyList()
    ) = ProductionBatch(
        id = batchId,
        restaurantId = restaurantId,
        recipeId = PreparationRecipeId("recipe-1"),
        recipeNameSnapshot = "Test Recipe",
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

    private fun createComponent(
        actualQuantity: BigDecimal = BigDecimal("5.00"),
        notes: String? = "Initial notes",
        sourceArea: InventoryAreaId? = areaId
    ) = ProductionBatchComponent(
        id = componentId,
        productionBatchId = batchId,
        sourceRecipeComponentIdSnapshot = "src-comp-1",
        componentIngredientId = ingredientId,
        recipeQuantityEnteredSnapshot = BigDecimal("5.00"),
        recipeQuantityBaseSnapshot = BigDecimal("5.00"),
        recipeUnitOptionIdSnapshot = unitOptionId,
        expectedQuantityEntered = BigDecimal("5.00"),
        expectedQuantityBase = BigDecimal("5.00"),
        actualQuantityEntered = actualQuantity,
        actualQuantityBase = actualQuantity,
        unitOptionId = unitOptionId,
        hasManualQuantityOverride = false,
        sourceAreaId = sourceArea,
        unitCostBaseSnapshot = null,
        totalCostSnapshot = null,
        sortOrder = 0,
        notes = notes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createIngredient() = Ingredient(
        id = ingredientId,
        restaurantId = restaurantId,
        name = "Test Ingredient",
        normalizedName = "test ingredient",
        categoryId = null,
        baseUnitId = UnitId("kg"),
        defaultAreaId = areaId,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createUnitOption(
        id: IngredientUnitOptionId = unitOptionId,
        displayName: String = "Kg"
    ) = IngredientUnitOption(
        id = id,
        ingredientId = ingredientId,
        displayName = displayName,
        shortLabel = displayName.lowercase(),
        standardUnitId = UnitId("kg"),
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createArea(id: InventoryAreaId = areaId) = InventoryArea(
        id = id,
        restaurantId = restaurantId,
        name = "Test Area",
        normalizedName = "test area",
        sortOrder = 0,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createViewModel(
        bId: String = batchId.value,
        cId: String = componentId.value
    ): ProductionBatchComponentViewModel {
        return ProductionBatchComponentViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            SavedStateHandle(mapOf("batchId" to bId, "componentId" to cId))
        )
    }

    private fun setupReadyState(
        actualQuantity: BigDecimal = BigDecimal("5.00"),
        notes: String? = "Initial notes",
        sourceArea: InventoryAreaId? = areaId
    ) {
        val component = createComponent(actualQuantity, notes, sourceArea)
        val batch = createBatch(components = listOf(component))

        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(any()) } returns createIngredient()
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(createUnitOption())
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(any()) } returns createArea()
    }

    @Test
    fun `data enrichment and initialization success`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals("Test Ingredient", state.ingredientName)
        assertEquals("5.00", state.actualQuantity)
        assertEquals("Initial notes", state.notes)
        assertEquals(unitOptionId, state.selectedUnitOptionId)
        assertEquals(areaId, state.selectedAreaId)
    }

    @Test
    fun `blank quantity rejected`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("  ")
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state.quantityError)
        assertNotNull(state.quantityErrorMessage)
        coVerify(exactly = 0) { productionBatchRepository.updateComponent(any()) }
    }

    @Test
    fun `malformed decimal rejected`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("1.2.3")
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state.quantityError)
        assertNotNull(state.quantityErrorMessage)
    }

    @Test
    fun `zero rejected`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("0")
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state.quantityError)
    }

    @Test
    fun `valid positive accepted`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("10.5")
        coEvery { productionBatchRepository.updateComponent(any()) } returns Unit
        
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertFalse(state.quantityError)
        coVerify { 
            productionBatchRepository.updateComponent(match { 
                it.actualQuantityEntered != null && it.actualQuantityEntered.compareTo(BigDecimal("10.5")) == 0 
            }) 
        }
    }

    @Test
    fun `unknown unit ID rejected during selection`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onUnitOptionSelected(IngredientUnitOptionId("unknown"))

        val state = viewModel.uiState.value
        assertNotNull(state.inlineError)
        assertEquals(unitOptionId, state.selectedUnitOptionId)
    }

    @Test
    fun `unit not in available options rejected during save`() = runTest {
        val batchFlow = MutableStateFlow<ProductionBatch?>(null)
        val component = createComponent(actualQuantity = BigDecimal("5"), notes = null, sourceArea = null)
        val batch = createBatch(components = listOf(component))

        every { productionBatchRepository.observeBatch(any()) } returns batchFlow
        coEvery { ingredientRepository.getById(any()) } returns createIngredient()
        
        val unit1 = createUnitOption(unitOptionId, "Unit 1")
        val unit2 = createUnitOption(IngredientUnitOptionId("unit2"), "Unit 2")

        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unit1, unit2)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        batchFlow.value = batch

        viewModel.onUnitOptionSelected(IngredientUnitOptionId("unit2"))
        assertEquals(IngredientUnitOptionId("unit2"), viewModel.uiState.value.selectedUnitOptionId)

        // Now change available units to only unit1
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unit1)
        
        // Trigger update with a fresh object
        val updatedBatch = createBatch(components = listOf(component)).copy(notes = "updated") // DIFFERENT OBJECT
        batchFlow.value = updatedBatch
        repeat(10) { runCurrent() }
        advanceUntilIdle()

        assertEquals(listOf(unit1), viewModel.uiState.value.availableUnitOptions)

        viewModel.onSave()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        viewModel.onSave()
        runCurrent()
        advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertEquals("Should have rejected invalid unit", 
            com.venkoi.cuentame.core.presentation.ui.UiMessage.Resource(com.venkoi.cuentame.R.string.error_unit_option_not_found), 
            currentState.inlineError)
        // Check for specific call count if exactly 0 fails
        coVerify(exactly = 0) { productionBatchRepository.updateComponent(any()) }
    }

    @Test
    fun `quantity error clears after editing`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("0")
        viewModel.onSave()
        assertTrue(viewModel.uiState.value.quantityError)

        viewModel.onQuantityChanged("10")
        assertFalse(viewModel.uiState.value.quantityError)
    }

    @Test
    fun `area-only update leaves other fields null in command`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        val newAreaId = InventoryAreaId("area-new")
        coEvery { inventoryAreaRepository.getById(any()) } returns createArea(newAreaId)
        viewModel.onAreaSelected(newAreaId)
        
        val commandSlot = slot<UpdateProductionBatchComponentCommand>()
        coEvery { productionBatchRepository.updateComponent(capture(commandSlot)) } returns Unit
        
        viewModel.onSave()

        val command = commandSlot.captured
        assertEquals(newAreaId, command.sourceAreaId)
        assertNull(command.actualQuantityEntered)
        assertNull(command.unitOptionId)
        assertNull(command.notes)
    }

    @Test
    fun `save typed failure sets inlineError`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("10")
        val exception = ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.ComponentQuantityMustBePositive))
        coEvery { productionBatchRepository.updateComponent(any()) } throws exception
        
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertNotNull(state.inlineError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `save success emits Saved exactly once`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()
        coEvery { productionBatchRepository.updateComponent(any()) } returns Unit
        
        val events = mutableListOf<ProductionBatchComponentEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onQuantityChanged("10")
        viewModel.onSave()
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchComponentEvent.Saved)
        assertFalse(viewModel.uiState.value.isSaving)
        
        job.cancel()
    }

    @Test
    fun `duplicate Save while isSaving invokes repository once`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        coEvery { productionBatchRepository.updateComponent(any()) } coAnswers {
            delay(1000)
            Unit
        }

        viewModel.onQuantityChanged("10")
        
        launch { viewModel.onSave() }
        runCurrent()
        
        assertTrue(viewModel.uiState.value.isSaving)
        
        viewModel.onSave() // Duplicate call
        
        advanceUntilIdle()
        coVerify(exactly = 1) { productionBatchRepository.updateComponent(any()) }
    }

    @Test
    fun `reset success updates form values`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("20")
        assertTrue(viewModel.uiState.value.quantityDirty)

        val resetComponent = createComponent(
            actualQuantity = BigDecimal("5.00"),
            notes = "Reset notes",
            sourceArea = areaId
        )
        coEvery { productionBatchRepository.resetComponentToExpected(any(), any()) } returns Unit
        coEvery { productionBatchRepository.getBatch(any()) } returns createBatch(components = listOf(resetComponent))

        viewModel.onResetToRecipe()

        val state = viewModel.uiState.value
        assertEquals("5.00", state.actualQuantity)
        assertFalse(state.quantityDirty)
    }

    @Test
    fun `reset failure sets inlineError`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("20")
        coEvery { productionBatchRepository.resetComponentToExpected(any(), any()) } throws RuntimeException("Reset failed")
        
        viewModel.onResetToRecipe()

        val state = viewModel.uiState.value
        assertNotNull(state.inlineError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `CancellationException on reset resets isSaving and does not set error`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        coEvery { productionBatchRepository.resetComponentToExpected(any(), any()) } coAnswers {
            delay(500)
            throw CancellationException()
        }
        
        launch { viewModel.onResetToRecipe() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)
        
        advanceTimeBy(600)
        
        val state = viewModel.uiState.value
        assertNull(state.inlineError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `loading failure sets screenState to LoadError with error_generic`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws RuntimeException("DB error")
        
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.screenState is ProductionBatchScreenState.LoadError)
        val error = (state.screenState as ProductionBatchScreenState.LoadError).message
        assertEquals(com.venkoi.cuentame.R.string.error_generic, (error as UiMessage.Resource).id)
    }

    @Test
    fun `save failure sets inlineError to error_generic and preserves form values`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("10.5")
        viewModel.onNotesChanged("Modified notes")
        
        coEvery { productionBatchRepository.updateComponent(any()) } throws RuntimeException("Network error")
        
        viewModel.onSave()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertEquals("10.5", state.actualQuantity)
        assertEquals("Modified notes", state.notes)
        assertEquals(com.venkoi.cuentame.R.string.error_generic, (state.inlineError as UiMessage.Resource).id)
        assertNotEquals(com.venkoi.cuentame.R.string.saved, (state.inlineError as UiMessage.Resource).id)
    }

    @Test
    fun `reset failure sets inlineError to error_generic and preserves manual values`() = runTest {
        setupReadyState()
        val viewModel = createViewModel()

        viewModel.onQuantityChanged("20")
        viewModel.onOverrideQuantity()
        
        coEvery { productionBatchRepository.resetComponentToExpected(any(), any()) } throws RuntimeException("Logic error")
        
        viewModel.onResetToRecipe()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertEquals("20", state.actualQuantity)
        assertTrue(state.hasManualOverride)
        assertEquals(com.venkoi.cuentame.R.string.error_generic, (state.inlineError as UiMessage.Resource).id)
    }
}
