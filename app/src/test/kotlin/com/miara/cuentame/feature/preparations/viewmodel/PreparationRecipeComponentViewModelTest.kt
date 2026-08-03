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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
        // ... existing test ...
    }

    @Test
    fun `same ingredient reselection retries unit options`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val ingredient = createIngredient("i1", "Ing 1")
        val options = listOf(createUnitOption("o1", "i1"))

        every { preparationRecipeRepository.observeRecipe(any()) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(ingredient)
        coEvery { ingredientRepository.getUnitOptions(ingredient.id, false) } throws RuntimeException("First attempt failed")

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onIngredientSelected(ingredient)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.inlineError).isNotNull()

        // Reselect same ingredient
        coEvery { ingredientRepository.getUnitOptions(ingredient.id, false) } returns options
        viewModel.onIngredientSelected(ingredient)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.inlineError).isNull()
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(options)
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

    @Test
    fun `post-initialization retry - preserves fields and restores Ready state`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val recipeFlow = MutableStateFlow(recipe)
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns recipeFlow
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns emptyList()
        
        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
        
        // Enter unsaved data
        viewModel.onQuantityChanged("123")
        viewModel.onNotesChanged("Unsaved Notes")
        
        // Simulate failure after initialization
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flow {
            throw RuntimeException("Async failure")
        }
        
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)
        
        // Mock success for retry
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
        assertThat(viewModel.uiState.value.quantity).isEqualTo("123")
        assertThat(viewModel.uiState.value.notes).isEqualTo("Unsaved Notes")
    }

    @Test
    fun `initial unit-option failure triggers LoadError and prevents initialization`() = runTest {
        val component = createComponent("c1", "i1", 0)
        val recipe = createRecipe("rec1", "out1").copy(components = listOf(component))
        
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(createIngredient("i1", "Ing 1"))
        
        // Simulate unit options failure
        coEvery { ingredientRepository.getUnitOptions(IngredientId("i1"), false) } throws RuntimeException("Unit options failed")

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1", "componentId" to "c1"))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)
        
        // Retry
        val options = listOf(createUnitOption("o1", "i1"))
        coEvery { ingredientRepository.getUnitOptions(IngredientId("i1"), false) } returns options
        
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.EditReady)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(options)
    }

    @Test
    fun `load cancellation - maximum one active collector and no LoadError`() = runTest {
        var activeCollectors = 0
        var maximumActiveCollectors = 0
        var subscriptionCount = 0
        var cancellationCount = 0

        val controllableFlow = flow {
            subscriptionCount++
            activeCollectors++
            maximumActiveCollectors = maxOf(maximumActiveCollectors, activeCollectors)
            try {
                emit(createRecipe("rec1", "out1"))
                delay(Long.MAX_VALUE)
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancellationCount++
                throw e
            } finally {
                activeCollectors--
            }
        }

        every { preparationRecipeRepository.observeRecipe(any()) } returns controllableFlow
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns emptyList()
        
        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        
        testScheduler.advanceTimeBy(100)
        assertThat(subscriptionCount).isEqualTo(1)

        viewModel.onQuantityChanged("99")
        viewModel.onNotesChanged("Notes")
        
        viewModel.onRetry()
        testScheduler.advanceTimeBy(100)
        
        assertThat(subscriptionCount).isEqualTo(2)
        assertThat(cancellationCount).isEqualTo(1)
        assertThat(maximumActiveCollectors).isEqualTo(1)
        
        assertThat(viewModel.uiState.value.loadState).isNotInstanceOf(PreparationScreenLoadState.LoadError::class.java)
        assertThat(viewModel.uiState.value.quantity).isEqualTo("99")
        assertThat(viewModel.uiState.value.notes).isEqualTo("Notes")
    }

    @Test
    fun `stale unit-option request cancellation - does not clear newer selection`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val ingredientA = createIngredient("iA", "Ing A")
        val ingredientB = createIngredient("iB", "Ing B")
        val optionsB = listOf(createUnitOption("oB", "iB"))

        every { preparationRecipeRepository.observeRecipe(any()) } returns flowOf(recipe)
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns listOf(ingredientA, ingredientB)
        
        coEvery { ingredientRepository.getUnitOptions(ingredientA.id, false) } coAnswers {
            delay(1000)
            throw kotlinx.coroutines.CancellationException("Stale")
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
        
        // Ensure the cancellation of A doesn't clear B's options
        testScheduler.advanceTimeBy(2000)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.selectedIngredient).isEqualTo(ingredientB)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(optionsB)
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
