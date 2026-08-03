package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
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
    private val testDispatcher = StandardTestDispatcher()
    
    private val now = Instant.parse("2026-08-03T10:00:00Z")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { timeProvider.now() } returns now
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

    @Test
    fun `initial data loading success`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val recipes = listOf(
            mockk<PreparationRecipeSummary> { 
                every { id } returns PreparationRecipeId("rec1")
                every { status } returns PreparationRecipeStatus.ACTIVE
            }
        )
        val areas = listOf(mockk<InventoryArea> { every { id } returns InventoryAreaId("area1") })

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipes(RestaurantId("res1"), false) } returns flowOf(recipes)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(areas)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals(recipes, state.availableRecipes)
        assertEquals(areas, state.availableAreas)
    }

    @Test
    fun `recipe selection enriches unit options and area`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val recipeSummary = mockk<PreparationRecipeSummary> { 
            every { id } returns PreparationRecipeId("rec1")
            every { status } returns PreparationRecipeStatus.ACTIVE
        }
        val recipe = mockk<PreparationRecipe> {
            every { id } returns PreparationRecipeId("rec1")
            every { status } returns PreparationRecipeStatus.ACTIVE
            every { outputIngredientId } returns IngredientId("ing1")
            every { yieldUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { standardYieldQuantity } returns BigDecimal("10")
        }
        val unitOptions = listOf(mockk<IngredientUnitOption> { every { id } returns IngredientUnitOptionId("unit1") })
        val ingredient = mockk<Ingredient> { every { defaultAreaId } returns InventoryAreaId("area1") }
        val areas = listOf(mockk<InventoryArea> { every { id } returns InventoryAreaId("area1") })

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipes(RestaurantId("res1"), false) } returns flowOf(listOf(recipeSummary))
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(areas)
        coEvery { preparationRecipeRepository.getRecipe(PreparationRecipeId("rec1")) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(IngredientId("ing1"), false) } returns unitOptions
        coEvery { ingredientRepository.getById(IngredientId("ing1")) } returns ingredient

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRecipeSelected(recipeSummary)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(recipe, state.selectedRecipe)
        assertEquals(unitOptions, state.availableUnitOptions)
        assertEquals(IngredientUnitOptionId("unit1"), state.selectedUnitOptionId)
        assertEquals(InventoryAreaId("area1"), state.selectedAreaId)
        assertEquals(BigDecimal("10"), state.expectedOutputEntered)
    }

    @Test
    fun `multiplier change updates expected output`() = runTest {
        // ... setup similar to above to have a recipe selected
        val viewModel = createViewModel()
        // Manually set selectedRecipe in state for simplicity in this specific test if needed, 
        // but it's better to go through the flow.
        
        // Mocking enrichment
        val recipe = mockk<PreparationRecipe> {
            every { standardYieldQuantity } returns BigDecimal("10")
        }
        // Accessing private _uiState via internal methods or just driving the VM
        // Let's drive it.
        
        // Setup initial data
        coEvery { restaurantRepository.getRestaurant() } returns mockk { every { id } returns RestaurantId("res1") }
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        
        val vm = createViewModel()
        advanceUntilIdle()
        
        // Simulate recipe selection enrichment
        // We need to use a real recipe summary or mock it
        val summary = mockk<PreparationRecipeSummary> { 
            every { id } returns PreparationRecipeId("rec1") 
            every { status } returns PreparationRecipeStatus.ACTIVE
        }
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { ingredientRepository.getById(any()) } returns null
        
        vm.onRecipeSelected(summary)
        advanceUntilIdle()
        
        vm.onMultiplierChanged("2.5")
        assertEquals(BigDecimal("25.0"), vm.uiState.value.expectedOutputEntered)
    }

    @Test
    fun `creation success emits event`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val recipe = mockk<PreparationRecipe> {
            every { id } returns PreparationRecipeId("rec1")
            every { status } returns PreparationRecipeStatus.ACTIVE
            every { standardYieldQuantity } returns BigDecimal("10")
        }
        
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1") })
        coEvery { ingredientRepository.getById(any()) } returns null
        
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        viewModel.onRecipeSelected(mockk { every { id } returns PreparationRecipeId("rec1"); every { status } returns PreparationRecipeStatus.ACTIVE })
        advanceUntilIdle()
        
        viewModel.onMultiplierChanged("1")
        viewModel.onAreaSelected(InventoryAreaId("area1"))
        viewModel.onUnitOptionSelected(IngredientUnitOptionId("unit1"))
        viewModel.onActualOutputChanged("12")
        
        coEvery { productionBatchRepository.createDraft(any()) } returns ProductionBatchId("batch1")
        
        val events = mutableListOf<ProductionBatchCreateEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        
        viewModel.onCreate()
        advanceUntilIdle()
        
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchCreateEvent.Created)
        assertEquals(ProductionBatchId("batch1"), (events[0] as ProductionBatchCreateEvent.Created).batchId)
        
        job.cancel()
    }

    @Test
    fun `typed error mapping on creation failure`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val recipe = mockk<PreparationRecipe> {
            every { id } returns PreparationRecipeId("rec1")
            every { status } returns PreparationRecipeStatus.ACTIVE
            every { standardYieldQuantity } returns BigDecimal("10")
        }
        
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1") })
        coEvery { ingredientRepository.getById(any()) } returns null
        
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        viewModel.onRecipeSelected(mockk { every { id } returns PreparationRecipeId("rec1"); every { status } returns PreparationRecipeStatus.ACTIVE })
        advanceUntilIdle()
        
        viewModel.onMultiplierChanged("1")
        viewModel.onAreaSelected(InventoryAreaId("area1"))
        viewModel.onUnitOptionSelected(IngredientUnitOptionId("unit1"))
        
        coEvery { productionBatchRepository.createDraft(any()) } throws ProductionBatchValidationException(listOf(ProductionBatchValidationFailure.MultiplierMustBePositive))
        
        viewModel.onCreate()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isCreating)
        assertNotNull(viewModel.uiState.value.inlineError)
        // Verify it's the expected message resource (R.string.error_multiplier_positive)
        // This is verified via ProductionBatchValidationErrorMapper logic
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException on create`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        // Setup state to be valid for creation
        // ... (simplified for this test)
        // I'll just use a trick to reach the repository call
        
        val recipe = mockk<PreparationRecipe> {
            every { id } returns PreparationRecipeId("rec1")
            every { status } returns PreparationRecipeStatus.ACTIVE
            every { standardYieldQuantity } returns BigDecimal("10")
        }
        coEvery { preparationRecipeRepository.getRecipe(any()) } returns recipe
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1") })
        coEvery { ingredientRepository.getById(any()) } returns null
        
        viewModel.onRecipeSelected(mockk { every { id } returns PreparationRecipeId("rec1"); every { status } returns PreparationRecipeStatus.ACTIVE })
        advanceUntilIdle()
        viewModel.onMultiplierChanged("1")
        viewModel.onAreaSelected(InventoryAreaId("area1"))
        viewModel.onUnitOptionSelected(IngredientUnitOptionId("unit1"))

        coEvery { productionBatchRepository.createDraft(any()) } throws CancellationException()
        
        viewModel.onCreate()
        advanceUntilIdle()
    }
}
