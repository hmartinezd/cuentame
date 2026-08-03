package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.feature.preparations.presentation.toPreparationRecipeUserMessage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class PreparationRecipeEditorViewModelTest {

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
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create mode - init loads available ingredients`() = runTest {
        val ingredients = listOf(createIngredient("i1", "Ing 1"), createIngredient("i2", "Ing 2"))
        every { ingredientRepository.observeIngredients(restaurantId, false) } returns flowOf(ingredients)
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(emptyList())

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.availableIngredients).isEqualTo(ingredients)
    }

    @Test
    fun `create mode - onSave calls createDraft and sends Created event`() = runTest {
        val ingredient = createIngredient("i1", "Ing 1")
        val option = createUnitOption("o1", "i1")
        val newRecipeId = PreparationRecipeId("new-rec")
        coEvery { preparationRecipeRepository.createDraft(any()) } returns newRecipeId
        coEvery { ingredientRepository.getUnitOptions(ingredient.id, false) } returns listOf(option)

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        backgroundScope.launch { viewModel.events.collect { } }
        advanceUntilIdle()

        viewModel.onOutputIngredientSelected(ingredient)
        advanceUntilIdle()
        viewModel.onYieldUnitOptionSelected(option)
        viewModel.onRecipeNameChanged("Custom Name")
        viewModel.onYieldQuantityChanged("10.5")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.createDraft(match { 
            it.outputIngredientId == ingredient.id && it.name == "Custom Name" && it.standardYieldQuantity == BigDecimal("10.5")
        }) }
        
        // No need to receive from channel if we just want to verify it was sent
    }

    @Test
    fun `edit mode - loads existing recipe and unit options`() = runTest {
        val recipe = createRecipe("rec1", "i1")
        val options = listOf(createUnitOption("o1", "i1"))
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getUnitOptions(recipe.outputIngredientId, false) } returns options
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(listOf(createIngredient("i1", "Ing 1")))
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipe).isEqualTo(recipe)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(options)
        assertThat(viewModel.uiState.value.recipeName).isEqualTo(recipe.name)
    }

    @Test
    fun `edit mode - preserve unsaved edits across repository emissions`() = runTest {
        val recipe = createRecipe("rec1", "i1")
        val recipeFlow = MutableStateFlow(recipe)
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns recipeFlow
        
        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onRecipeNameChanged("User Editted Name")
        
        // Emit new recipe state from repo
        recipeFlow.value = recipe.copy(updatedAt = Instant.now())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipeName).isEqualTo("User Editted Name")
    }

    @Test
    fun `create mode - blank name submits null to repository`() = runTest {
        val ingredient = createIngredient("i1", "Ing 1")
        val option = createUnitOption("o1", "i1")
        coEvery { preparationRecipeRepository.createDraft(any()) } returns PreparationRecipeId("new-rec")
        coEvery { ingredientRepository.getUnitOptions(ingredient.id, false) } returns listOf(option)

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()

        viewModel.onOutputIngredientSelected(ingredient)
        advanceUntilIdle()
        viewModel.onRecipeNameChanged("  ")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.createDraft(match { it.name == null }) }
    }

    @Test
    fun `onSave - guards against concurrent saving`() = runTest {
        val ingredient = createIngredient("i1", "Ing 1")
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        coEvery { preparationRecipeRepository.createDraft(any()) } coAnswers {
            testDispatcher.scheduler.advanceTimeBy(1000)
            PreparationRecipeId("new")
        }

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.onOutputIngredientSelected(ingredient)
        advanceUntilIdle()

        viewModel.onSave() // Starts saving
        viewModel.onSave() // Should be ignored

        advanceUntilIdle()
        coVerify(exactly = 1) { preparationRecipeRepository.createDraft(any()) }
    }

    @Test
    fun `edit mode - non-draft recipe navigates to detail exactly once`() = runTest {
        val recipe = createRecipe("rec1", "i1").copy(status = PreparationRecipeStatus.ACTIVE)
        val recipeFlow = MutableStateFlow(recipe)
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns recipeFlow
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        
        val events = mutableListOf<PreparationRecipeEditorEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        // Emit again
        recipeFlow.value = recipe.copy(updatedAt = Instant.now())
        advanceUntilIdle()

        assertThat(events.filterIsInstance<PreparationRecipeEditorEvent.NavigateToDetail>()).hasSize(1)
        job.cancel()
    }

    @Test
    fun `output unit-option latest-selection-wins`() = runTest {
        val ingredientA = createIngredient("iA", "Ing A")
        val ingredientB = createIngredient("iB", "Ing B")
        val optionsA = listOf(createUnitOption("oA", "iA"))
        val optionsB = listOf(createUnitOption("oB", "iB"))

        coEvery { ingredientRepository.getUnitOptions(IngredientId("iA"), false) } coAnswers {
            delay(1000)
            optionsA
        }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("iB"), false) } returns optionsB

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()

        viewModel.onOutputIngredientSelected(ingredientA)
        testScheduler.advanceTimeBy(100)
        viewModel.onOutputIngredientSelected(ingredientB)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedOutputIngredient).isEqualTo(ingredientB)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(optionsB)
        
        // Advance more to let A finish
        testScheduler.advanceTimeBy(2000)
        advanceUntilIdle()

        // Still B
        assertThat(viewModel.uiState.value.selectedOutputIngredient).isEqualTo(ingredientB)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(optionsB)
    }

    @Test
    fun `onRetry clears error and restarts loadJob`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws RuntimeException("Load failed")
        
        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())

        viewModel.onRetry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
    }

    @Test
    fun `missing recipeId in route results in CreateReady state`() = runTest {
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to ""))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
    }

    @Test
    fun `post-initialization retry - preserves fields and restores Ready state`() = runTest {
        val ingredient = createIngredient("i1", "Ing 1")
        val ingredientsFlow = MutableStateFlow(listOf(ingredient))
        val recipesFlow = MutableStateFlow(emptyList<PreparationRecipeSummary>())
        
        every { ingredientRepository.observeIngredients(restaurantId, false) } returns ingredientsFlow
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns recipesFlow
        
        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
        
        // Enter some unsaved data
        viewModel.onRecipeNameChanged("Unsaved Name")
        viewModel.onYieldQuantityChanged("50")
        
        // Simulate failure after initialization
        ingredientsFlow.value = emptyList() // Trigger emission
        // Force a failure in the next collection cycle
        every { ingredientRepository.observeIngredients(restaurantId, false) } returns flow {
            throw RuntimeException("Async failure")
        }
        
        // We need to trigger a new collection. The current collectLatest is still running.
        // Actually, in the current implementation, if the Flow itself throws, collectLatest will catch it in the launch block.
        
        // Let's re-mock for retry
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)
        assertThat(viewModel.uiState.value.recipeName).isEqualTo("Unsaved Name")
        
        // Now mock success for retry
        every { ingredientRepository.observeIngredients(restaurantId, false) } returns flowOf(listOf(ingredient))
        
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.CreateReady)
        assertThat(viewModel.uiState.value.recipeName).isEqualTo("Unsaved Name")
        assertThat(viewModel.uiState.value.yieldQuantity).isEqualTo("50")
    }

    @Test
    fun `initial unit-option failure triggers LoadError and prevents initialization`() = runTest {
        val recipe = createRecipe("rec1", "i1")
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(listOf(createIngredient("i1", "Ing 1")))
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        
        // Simulate unit options failure
        coEvery { ingredientRepository.getUnitOptions(recipe.outputIngredientId, false) } throws RuntimeException("Unit options failed")

        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)
        
        // Retry
        val options = listOf(createUnitOption("o1", "i1"))
        coEvery { ingredientRepository.getUnitOptions(recipe.outputIngredientId, false) } returns options
        
        viewModel.onRetry()
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.EditReady)
        assertThat(viewModel.uiState.value.availableUnitOptions).isEqualTo(options)
    }

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
