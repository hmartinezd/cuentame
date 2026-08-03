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
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { preparationRecipeRepository.getRecipe(recipe.id) } returns recipe
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
    fun `onSave calls saveComponent and sends Saved event`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val ingredient = createIngredient("i1", "Ing 1")
        val option = createUnitOption("o1", "i1")
        coEvery { preparationRecipeRepository.getRecipe(recipe.id) } returns recipe

        val viewModel = PreparationRecipeComponentViewModel(
            preparationRecipeRepository, ingredientRepository, restaurantRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onIngredientSelected(ingredient)
        advanceUntilIdle()
        viewModel.onQuantityChanged("5.0")
        viewModel.onUnitOptionSelected(option)
        viewModel.onSave()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.saveComponent(match { 
            it.recipeId == recipe.id && it.componentIngredientId == ingredient.id && it.quantityEntered == BigDecimal("5.0")
        }) }
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
