package com.venkoi.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.menu.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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
    private val creation = mockk<MenuItemCreationRepository>()
    private val ingredients = mockk<IngredientRepository>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { catalogs.observeMenu(menuId) } returns flowOf(Menu(menuId, restaurantId, "Dinner", "dinner", null, BigDecimal("3"), 0, null, Instant.EPOCH, Instant.EPOCH))
        every { catalogs.observeCategories(menuId) } returns flowOf(listOf(MenuCategory(categoryId, menuId, "Entrees", "entrees", 0)))
        every { catalogs.observePlacements(menuId) } returns flowOf(emptyList())
        every { ingredients.observeIngredients(restaurantId, false) } returns flowOf(listOf(ingredient()))
        coEvery { ingredients.getUnitOptions(ingredientId, false) } returns listOf(option())
        coEvery { creation.create(any()) } returns MenuRecipeId("created")
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `save persists commercial settings components and originating placement`() = runTest {
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.setCashDiscountBehavior(CashDiscountBehavior.NONE)
        vm.openComponent(); vm.selectIngredient(ingredientId); advanceUntilIdle(); vm.updateQuantity("6"); vm.saveComponent(); advanceUntilIdle()
        vm.save("Chicken", "18.50"); advanceUntilIdle()

        assertThat(vm.state.value.saved).isTrue()
        assertThat(vm.state.value.defaultDiscountPercent.compareTo(BigDecimal("3"))).isEqualTo(0)
        coVerify(exactly = 1) { creation.create(match { request ->
            request.menuId == menuId && request.categoryId == categoryId && request.cashDiscountBehavior == CashDiscountBehavior.NONE &&
                request.components.single().quantity.compareTo(BigDecimal("6")) == 0
        }) }
        job.cancel()
    }

    @Test fun `item without ingredients remains saveable`() = runTest {
        every { ingredients.observeIngredients(restaurantId, false) } returns flowOf(emptyList())
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        assertThat(vm.state.value.ingredients).isEmpty()
        vm.save("Soup", "9"); advanceUntilIdle()
        assertThat(vm.state.value.saved).isTrue()
        coVerify(exactly = 1) { creation.create(match { it.components.isEmpty() }) }
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

    @Test fun `atomic creation failure remains retryable without local recovery state`() = runTest {
        coEvery { creation.create(any()) } throws IllegalStateException("failed")
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.save("Soup", "9"); advanceUntilIdle()
        assertThat(vm.state.value.saved).isFalse()
        assertThat(vm.state.value.error).isEqualTo(MenuOperationError.SAVE_FAILED)
        coVerify(exactly = 1) { creation.create(any()) }
        job.cancel()
    }

    @Test fun `editing a draft component updates it without triggering duplicate validation`() = runTest {
        val vm = viewModel(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }; runCurrent()
        vm.openComponent(); vm.selectIngredient(ingredientId); advanceUntilIdle(); vm.updateQuantity("6"); vm.saveComponent()
        val component = vm.state.first { it.components.size == 1 }.components.single()
        vm.openComponent(component); advanceUntilIdle(); vm.updateQuantity("8"); vm.saveComponent(); advanceUntilIdle()
        assertThat(vm.state.value.components).hasSize(1)
        assertThat(vm.state.value.components.single().quantity.compareTo(BigDecimal("8"))).isEqualTo(0)
        job.cancel()
    }

    private fun viewModel() = CreateMenuItemViewModel(SavedStateHandle(mapOf("menuId" to menuId.value, "categoryId" to categoryId.value)), catalogs, creation, ingredients)
    private fun ingredient() = Ingredient(ingredientId, restaurantId, "Chicken", "chicken", baseUnitId = UnitId("oz"), isActive = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private fun option() = IngredientUnitOption(optionId, ingredientId, "Ounce", "oz", UnitId("oz"), BigDecimal.ONE, true, true, false, true, Instant.EPOCH, Instant.EPOCH)
}
