package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.MenuCostRepository
import com.miara.cuentame.core.domain.repository.MenuRecipeRepository
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
import com.miara.cuentame.core.model.menu.MenuRecipe
import com.miara.cuentame.core.model.menu.MenuRecipeCost
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MenuDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val recipeId = MenuRecipeId("recipe")
    private val restaurantId = RestaurantId("restaurant")
    private val recipes = mockk<MenuRecipeRepository>()
    private val costs = mockk<MenuCostRepository>()
    private val ingredients = mockk<IngredientRepository>()
    private var recipeObservationCount = 0
    private var failRecipeObservation = true

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { recipes.observeRecipe(recipeId) } answers { recipeObservation() }
        every { recipes.observeComponents(recipeId) } returns flowOf(emptyList())
        every { ingredients.observeIngredients(restaurantId, false) } returns flowOf(emptyList())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `retry observes detail again and loads after initial failure`() = runTest {
        val cost = mockk<MenuRecipeCost>()
        every { costs.observeCost(recipeId) } returns flowOf(cost)
        val viewModel = MenuDetailViewModel(SavedStateHandle(mapOf("menuRecipeId" to recipeId.value)), recipes, costs, ingredients)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        assertThat(viewModel.state.value.error).isTrue()
        assertThat(recipeObservationCount).isEqualTo(1)

        failRecipeObservation = false
        viewModel.retry()
        runCurrent()

        assertThat(recipeObservationCount).isEqualTo(2)
        assertThat(viewModel.state.value.error).isFalse()
        assertThat(viewModel.state.value.recipe).isEqualTo(recipe())
        assertThat(viewModel.state.value.cost).isSameInstanceAs(cost)
        collection.cancel()
    }

    private fun recipeObservation(): Flow<MenuRecipe?> = flow {
        recipeObservationCount++
        if (failRecipeObservation) throw IllegalStateException("load failed")
        emit(recipe())
    }

    private fun recipe() = MenuRecipe(recipeId, restaurantId, "Soup", "soup", BigDecimal.TEN, null,
        CashDiscountBehavior.APPLY_DEFAULT, 0, 0, null, Instant.EPOCH, Instant.EPOCH)
}
