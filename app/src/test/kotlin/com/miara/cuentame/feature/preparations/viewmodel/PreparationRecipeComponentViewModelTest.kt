package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.ingredient.PreparationRecipeComponent
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PreparationRecipeComponentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preparationRecipeRepository = mockk<PreparationRecipeRepository>(relaxed = true)
    private val ingredientRepository = mockk<IngredientRepository>(relaxed = true)
    private val restaurantRepository = mockk<RestaurantRepository>()
    
    private val restaurantId = RestaurantId("r1")
    private val restaurant = Restaurant(restaurantId, "Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads recipe and available ingredients`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val ingredients = listOf(createIngredient("i1", "Ing 1"), createIngredient("out1", "Out"))
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(restaurantId, false) } returns ingredients

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        // Should exclude output ingredient
        assertThat(viewModel.uiState.value.availableIngredients).containsExactly(ingredients[0])
    }

    @Test
    fun `create mode - assigns next sort order`() = runTest {
        val component = createComponent("c1", "i1", 0)
        val recipe = createRecipe("rec1", "out1").copy(components = listOf(component))
        val ingredient = createIngredient("i2", "Ing 2")
        val option = createUnitOption("o2", "i2")
        
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(ingredient)
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(option)

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onIngredientSelected(ingredient)
        advanceUntilIdle()
        viewModel.onQuantityChanged("5")
        viewModel.onUnitOptionSelected(option)
        viewModel.onSave()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.saveComponent(match { it.sortOrder == 1 }) }
    }

    @Test
    fun `edit mode - preserves existing sort order`() = runTest {
        val component = createComponent("c1", "i1", 5)
        val recipe = createRecipe("rec1", "out1").copy(components = listOf(component))
        val ingredient = createIngredient("i1", "Ing 1")
        val option = createUnitOption("o1", "i1")

        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(ingredient)
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(option)

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1", "componentId" to "c1"))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mode).isInstanceOf(PreparationRecipeComponentMode.Edit::class.java)
        viewModel.onQuantityChanged("10")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.saveComponent(match { it.sortOrder == 5 }) }
    }

    @Test
    fun `ingredient selection latest-selection-wins`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val ingredientA = createIngredient("iA", "Ing A")
        val ingredientB = createIngredient("iB", "Ing B")
        val optionsA = listOf(createUnitOption("oA", "iA"))
        val optionsB = listOf(createUnitOption("oB", "iB"))

        every { preparationRecipeRepository.observeRecipe(any()) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(ingredientA, ingredientB)
        coEvery { ingredientRepository.getUnitOptions(ingredientA.id, false) } coAnswers {
            kotlinx.coroutines.delay(1000)
            optionsA
        }
        coEvery { ingredientRepository.getUnitOptions(ingredientB.id, false) } returns optionsB

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onIngredientSelected(ingredientA)
        testScheduler.advanceTimeBy(100)
        viewModel.onIngredientSelected(ingredientB)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedIngredient).isEqualTo(ingredientB)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(optionsB)
        
        testScheduler.advanceTimeBy(2000)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(optionsB)
    }

    @Test
    fun `missing recipeId leads to InvalidRoute state`() = runTest {
        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to ""))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.InvalidRoute)
    }

    @Test
    fun `parent recipe not draft leads to ParentNotEditable state and navigation event`() = runTest {
        val recipe = createRecipe("rec1", "out1").copy(status = PreparationRecipeStatus.ACTIVE)
        every { preparationRecipeRepository.observeRecipe(any()) } returns flowOf(recipe)

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        
        val events = mutableListOf<PreparationRecipeComponentEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.ParentNotEditable)
        assertThat(events).contains(PreparationRecipeComponentEvent.NavigateToDetail(recipe.id))
    }

    @Test
    fun `onRetry reloads data after failure`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws RuntimeException("Error")

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { preparationRecipeRepository.observeRecipe(any()) } returns flowOf(createRecipe("rec1", "out1"))
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns emptyList()

        viewModel.onRetry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
    }

    private fun createComponent(id: String, ingId: String, sortOrder: Int) = PreparationRecipeComponent(
        id = PreparationRecipeComponentId(id),
        recipeId = PreparationRecipeId("rec1"),
        componentIngredientId = IngredientId(ingId),
        unitOptionId = IngredientUnitOptionId("o1"),
        quantityEntered = BigDecimal.ONE,
        quantityBase = BigDecimal.ONE,
        sortOrder = sortOrder,
        notes = null
    )

    private fun createIngredient(id: String, name: String) = Ingredient(
        id = IngredientId(id),
        restaurantId = restaurantId,
        name = name,
        normalizedName = name.lowercase(),
        categoryId = null,
        baseUnitId = com.miara.cuentame.core.common.ids.UnitId("u1"),
        defaultAreaId = com.miara.cuentame.core.common.ids.InventoryAreaId("a1"),
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null
    )

    private fun createRecipe(id: String, outputId: String) = PreparationRecipe(
        id = PreparationRecipeId(id),
        restaurantId = restaurantId,
        outputIngredientId = IngredientId(outputId),
        name = "Recipe $id",
        standardYieldQuantity = BigDecimal.ONE,
        standardYieldQuantityBase = BigDecimal.ONE,
        yieldUnitOptionId = IngredientUnitOptionId("o1"),
        status = PreparationRecipeStatus.DRAFT,
        notes = null,
        components = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null
    )

    private fun createUnitOption(id: String, ingId: String) = IngredientUnitOption(
        id = IngredientUnitOptionId(id),
        ingredientId = IngredientId(ingId),
        displayName = "Opt $id",
        shortLabel = "o",
        standardUnitId = null,
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null
    )
}
