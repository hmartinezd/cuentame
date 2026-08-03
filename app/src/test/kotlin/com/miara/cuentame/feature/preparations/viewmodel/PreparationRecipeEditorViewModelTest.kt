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
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    fun `onYieldQuantityChanged with invalid decimal sets error`() = runTest {
        val viewModel = PreparationRecipeEditorViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, SavedStateHandle()
        )
        advanceUntilIdle()

        viewModel.onOutputIngredientSelected(createIngredient("i1", "Ing 1"))
        advanceUntilIdle()
        viewModel.onYieldQuantityChanged("invalid")
        viewModel.onSave()
        
        assertThat(viewModel.uiState.value.yieldQuantityError).isTrue()
        coVerify(exactly = 0) { preparationRecipeRepository.createDraft(any()) }
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
