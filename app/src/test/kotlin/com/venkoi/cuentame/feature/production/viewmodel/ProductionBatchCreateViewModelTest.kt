package com.venkoi.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationException
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.venkoi.cuentame.core.model.ingredient.*
import com.venkoi.cuentame.core.model.inventory.InventoryArea
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ProductionBatchCreateViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val preparationRecipeRepository = mockk<PreparationRecipeRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val now = Instant.parse("2026-08-03T10:00:00Z")

    private val testRestaurant = Restaurant(
        id = RestaurantId("res1"),
        name = "Test Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = now,
        updatedAt = now
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { timeProvider.now() } returns now
        coEvery { restaurantRepository.getRestaurant() } returns testRestaurant
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(recipeId: String? = null): ProductionBatchCreateViewModel {
        return ProductionBatchCreateViewModel(
            productionBatchRepository,
            preparationRecipeRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            timeProvider,
            SavedStateHandle(if (recipeId != null) mapOf("recipeId" to recipeId) else emptyMap())
        )
    }

    private fun createRecipeSummary(id: String, status: PreparationRecipeStatus = PreparationRecipeStatus.ACTIVE) = PreparationRecipeSummary(
        id = PreparationRecipeId(id),
        outputIngredientId = IngredientId("ing_$id"),
        outputIngredientName = "Ingredient $id",
        recipeName = "Recipe $id",
        status = status,
        standardYieldQuantity = BigDecimal.TEN,
        yieldUnitLabel = "kg",
        componentCount = 5,
        updatedAt = now
    )

    private fun createRecipe(id: String, status: PreparationRecipeStatus = PreparationRecipeStatus.ACTIVE) = PreparationRecipe(
        id = PreparationRecipeId(id),
        restaurantId = testRestaurant.id,
        outputIngredientId = IngredientId("ing_$id"),
        name = "Recipe $id",
        standardYieldQuantity = BigDecimal.TEN,
        standardYieldQuantityBase = BigDecimal.TEN,
        yieldUnitOptionId = IngredientUnitOptionId("unit_$id"),
        status = status,
        notes = null,
        components = emptyList(),
        createdAt = now,
        updatedAt = now,
        archivedAt = null
    )

    private fun createUnitOption(id: String, ingredientId: String) = IngredientUnitOption(
        id = IngredientUnitOptionId(id),
        ingredientId = IngredientId(ingredientId),
        displayName = "Unit $id",
        shortLabel = "u$id",
        standardUnitId = UnitId("kg"),
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    private fun createIngredient(id: String, defaultAreaId: String? = null) = Ingredient(
        id = IngredientId(id),
        restaurantId = testRestaurant.id,
        name = "Ingredient $id",
        normalizedName = "ingredient $id",
        baseUnitId = UnitId("kg"),
        defaultAreaId = defaultAreaId?.let { InventoryAreaId(it) },
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    private fun createArea(id: String, name: String) = InventoryArea(
        id = InventoryAreaId(id),
        restaurantId = testRestaurant.id,
        name = name,
        normalizedName = name.lowercase(),
        sortOrder = 0,
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `initial data loading success`() = runTest {
        val summaries = listOf(createRecipeSummary("rec1"))
        val areas = listOf(createArea("area1", "Area 1"))

        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(summaries)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(areas)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals(summaries, state.availableRecipes)
        assertEquals(areas, state.availableAreas)
    }

    @Test
    fun `recipe selection enriches unit options and area`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOptions = listOf(createUnitOption("unit_rec1", "ing_rec1"))
        val ingredient = createIngredient("ing_rec1", defaultAreaId = "area1")
        val area = createArea("area1", "Area 1")

        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns unitOptions
        coEvery { ingredientRepository.getById(any()) } returns ingredient

        val viewModel = createViewModel()
        
        viewModel.onRecipeSelected(summary)

        val state = viewModel.uiState.value
        assertEquals(recipe, state.selectedRecipe)
        assertEquals(unitOptions, state.availableUnitOptions)
        assertEquals(IngredientUnitOptionId("unit_rec1"), state.selectedUnitOptionId)
        assertEquals(InventoryAreaId("area1"), state.selectedAreaId)
        assertNotNull(state.expectedOutputEntered)
        assertEquals(0, BigDecimal("10").compareTo(state.expectedOutputEntered!!))
    }

    @Test
    fun `multiplier change updates expected output`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { ingredientRepository.getById(any()) } returns null
        
        val viewModel = createViewModel()
        viewModel.onRecipeSelected(summary)
        
        viewModel.onMultiplierChanged("2.5")
        val state = viewModel.uiState.value
        assertNotNull(state.expectedOutputEntered)
        assertEquals(0, BigDecimal("25.0").compareTo(state.expectedOutputEntered!!))
    }

    @Test
    fun `creation success emits event and resets isCreating`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOption = createUnitOption("unit_rec1", "ing_rec1")
        val area = createArea("area1", "Area 1")
        
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unitOption)
        coEvery { ingredientRepository.getById(any()) } returns null
        
        val viewModel = createViewModel()
        viewModel.onRecipeSelected(summary)
        runCurrent()
        
        viewModel.onMultiplierChanged("1")
        viewModel.onAreaSelected(area.id)
        viewModel.onUnitOptionSelected(unitOption.id)
        
        coEvery { productionBatchRepository.createDraft(any()) } returns ProductionBatchId("batch1")
        
        val eventReceived = kotlinx.coroutines.CompletableDeferred<ProductionBatchCreateEvent>()
        val job = launch { viewModel.events.collect { eventReceived.complete(it) } }
        runCurrent()
        
        viewModel.onCreate()
        
        val event = eventReceived.await()
        assertTrue(event is ProductionBatchCreateEvent.Created)
        assertEquals(ProductionBatchId("batch1"), (event as ProductionBatchCreateEvent.Created).batchId)
        assertFalse(viewModel.uiState.value.isCreating)
        
        job.cancel()
    }

    @Test
    fun `typed error mapping on creation failure`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOption = createUnitOption("unit_rec1", "ing_rec1")
        val area = createArea("area1", "Area 1")
        
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unitOption)
        coEvery { ingredientRepository.getById(any()) } returns null
        
        val viewModel = createViewModel()
        viewModel.onRecipeSelected(summary)
        
        viewModel.onMultiplierChanged("1")
        viewModel.onAreaSelected(area.id)
        viewModel.onUnitOptionSelected(unitOption.id)
        
        val exception = ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
        coEvery { productionBatchRepository.createDraft(any()) } throws exception
        
        viewModel.onCreate()
        
        assertFalse(viewModel.uiState.value.isCreating)
        assertNotNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun `preselected active recipe enriches and selects automatically`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOptions = listOf(createUnitOption("unit_rec1", "ing_rec1"))
        val ingredient = createIngredient("ing_rec1", defaultAreaId = "area1")
        val area = createArea("area1", "Area 1")

        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns unitOptions
        coEvery { ingredientRepository.getById(any()) } returns ingredient

        val viewModel = createViewModel() // NO preselection in init
        runCurrent()
        
        viewModel.onRecipeSelected(summary) // Select manually
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("Unexpected inline error: ${state.inlineError}", state.inlineError)
        assertEquals(recipe, state.selectedRecipe)
        assertEquals(InventoryAreaId("area1"), state.selectedAreaId)
        assertEquals(IngredientUnitOptionId("unit_rec1"), state.selectedUnitOptionId)
    }

    @Test
    fun `recipe switch clears previous area`() = runTest {
        val summaryA = createRecipeSummary("recA")
        val summaryB = createRecipeSummary("recB")
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summaryA, summaryB))
        
        val area1 = createArea("area1", "Area 1")
        val area2 = createArea("area2", "Area 2")
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area1, area2))

        val recipeA = createRecipe("recA").copy(outputIngredientId = IngredientId("ingA"))
        val recipeB = createRecipe("recB").copy(outputIngredientId = IngredientId("ingB"))

        coEvery { preparationRecipeRepository.getRecipe(PreparationRecipeId("recA")) } returns recipeA
        coEvery { preparationRecipeRepository.getRecipe(PreparationRecipeId("recB")) } returns recipeB
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { ingredientRepository.getById(IngredientId("ingA")) } returns createIngredient("ingA", defaultAreaId = "area1")
        coEvery { ingredientRepository.getById(IngredientId("ingB")) } returns createIngredient("ingB", defaultAreaId = "area2")

        val viewModel = createViewModel()

        viewModel.onRecipeSelected(summaryA)
        assertEquals(InventoryAreaId("area1"), viewModel.uiState.value.selectedAreaId)

        viewModel.onRecipeSelected(summaryB)
        assertEquals(InventoryAreaId("area2"), viewModel.uiState.value.selectedAreaId)
    }

    @Test
    fun `future effective time rejects creation`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOption = createUnitOption("unit_rec1", "ing_rec1")
        val area = createArea("area1", "Area 1")

        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unitOption)
        coEvery { ingredientRepository.getById(any()) } returns null

        val viewModel = createViewModel()
        viewModel.onRecipeSelected(summary)
        viewModel.onAreaSelected(area.id)
        viewModel.onUnitOptionSelected(unitOption.id)

        viewModel.onEffectiveAtChanged(now.plusSeconds(3600))
        
        viewModel.onCreate()

        assertNotNull(viewModel.uiState.value.inlineError)
        coVerify(exactly = 0) { productionBatchRepository.createDraft(any()) }
    }

    @Test
    fun `invalid actual output rejects creation`() = runTest {
        val summary = createRecipeSummary("rec1")
        val recipe = createRecipe("rec1")
        val unitOption = createUnitOption("unit_rec1", "ing_rec1")
        val area = createArea("area1", "Area 1")

        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(listOf(summary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(area))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(unitOption)
        coEvery { ingredientRepository.getById(any()) } returns null

        val viewModel = createViewModel()
        viewModel.onRecipeSelected(summary)
        viewModel.onAreaSelected(area.id)
        viewModel.onUnitOptionSelected(unitOption.id)

        viewModel.onActualOutputChanged("abc")
        viewModel.onCreate()
        assertTrue(viewModel.uiState.value.actualOutputError)

        viewModel.onActualOutputChanged("-5")
        viewModel.onCreate()
        assertTrue(viewModel.uiState.value.actualOutputError)
        
        coVerify(exactly = 0) { productionBatchRepository.createDraft(any()) }
    }
}
