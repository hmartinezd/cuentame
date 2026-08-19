package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.menu.*
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
class CreateMenuItemViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val menuId = MenuId("menu")
    private val categoryId = MenuCategoryId("category")
    private val restaurantId = RestaurantId("restaurant")
    private val ingredientId = IngredientId("ingredient")
    private val optionId = IngredientUnitOptionId("option")
    private val catalogs = mockk<MenuCatalogRepository>()
    private val recipes = mockk<MenuRecipeRepository>()
    private val ingredients = mockk<IngredientRepository>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { catalogs.observeMenu(menuId) } returns flowOf(Menu(menuId, restaurantId, "Dinner", "dinner", null, BigDecimal("3"), 0, null, Instant.EPOCH, Instant.EPOCH))
        every { catalogs.observeCategories(menuId) } returns flowOf(listOf(MenuCategory(categoryId, menuId, "Entrees", "entrees", 0)))
        every { catalogs.observePlacements(menuId) } returns flowOf(emptyList())
        every { ingredients.observeIngredients(restaurantId, false) } returns flowOf(listOf(ingredient()))
        coEvery { ingredients.getUnitOptions(ingredientId, false) } returns listOf(option())
        coEvery { recipes.create(any(), any(), any(), any()) } returns MenuRecipeId("created")
        coEvery { recipes.setCashDiscountBehavior(any(), any()) } just Runs
        coEvery { recipes.saveComponent(any(), any(), any(), any(), any(), any()) } returns MenuRecipeComponentId("component")
        coEvery { catalogs.savePlacement(any(), any(), any(), any(), any()) } returns MenuPlacementId("placement")
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `save persists commercial settings components and originating placement`() = runTest {
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.setCashDiscountBehavior(CashDiscountBehavior.NONE)
        vm.openComponent(); vm.selectIngredient(ingredientId); advanceUntilIdle(); vm.updateQuantity("6"); vm.saveComponent()
        vm.save("Chicken", "18.50"); advanceUntilIdle()

        assertThat(vm.state.value.saved).isTrue()
        assertThat(vm.state.value.defaultDiscountPercent.compareTo(BigDecimal("3"))).isEqualTo(0)
        coVerify(exactly = 1) { recipes.setCashDiscountBehavior(MenuRecipeId("created"), CashDiscountBehavior.NONE) }
        coVerify(exactly = 1) { recipes.saveComponent(MenuRecipeId("created"), null, ingredientId, optionId, BigDecimal("6"), 0) }
        coVerify(exactly = 1) { catalogs.savePlacement(menuId, null, categoryId, MenuRecipeId("created"), 0) }
        job.cancel()
    }

    @Test fun `item without ingredients remains saveable`() = runTest {
        every { ingredients.observeIngredients(restaurantId, false) } returns flowOf(emptyList())
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        assertThat(vm.state.value.ingredients).isEmpty()
        vm.save("Soup", "9"); advanceUntilIdle()
        assertThat(vm.state.value.saved).isTrue()
        coVerify(exactly = 0) { recipes.saveComponent(any(), any(), any(), any(), any(), any()) }
        job.cancel()
    }

    @Test fun `invalid and duplicate draft components are rejected before persistence`() = runTest {
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.openComponent(); vm.selectIngredient(ingredientId); advanceUntilIdle(); vm.updateQuantity("0"); vm.saveComponent(); runCurrent()
        assertThat(vm.state.value.editor.error).isEqualTo(MenuOperationError.INVALID_QUANTITY)
        vm.updateQuantity("1"); vm.saveComponent(); vm.openComponent(); vm.selectIngredient(ingredientId); advanceUntilIdle(); vm.updateQuantity("2"); vm.saveComponent(); runCurrent()
        assertThat(vm.state.value.editor.error).isEqualTo(MenuOperationError.DUPLICATE_COMPONENT)
        job.cancel()
    }

    @Test fun `placement failure keeps persisted recipe and shows recovery`() = runTest {
        coEvery { catalogs.savePlacement(any(), any(), any(), any(), any()) } throws IllegalStateException("failed")
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.save("Soup", "9"); advanceUntilIdle()
        assertThat(vm.state.value.saved).isFalse()
        assertThat(vm.state.value.placementRecoveryNeeded).isTrue()
        coVerify(exactly = 1) { recipes.create(restaurantId, "Soup", BigDecimal("9"), null) }
        job.cancel()
    }

    private fun viewModel() = CreateMenuItemViewModel(SavedStateHandle(mapOf("menuId" to menuId.value, "categoryId" to categoryId.value)), catalogs, recipes, ingredients)
    private fun ingredient() = Ingredient(ingredientId, restaurantId, "Chicken", "chicken", baseUnitId = UnitId("oz"), isActive = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private fun option() = IngredientUnitOption(optionId, ingredientId, "Ounce", "oz", UnitId("oz"), BigDecimal.ONE, true, true, false, true, Instant.EPOCH, Instant.EPOCH)
}
