package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MenuCatalogDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val restaurantId = RestaurantId("restaurant")
    private val menuId = MenuId("menu")
    private val categoryId = MenuCategoryId("category")
    private val archived = recipe("archived", archived = true)
    private val activePlaced = recipe("placed")
    private val activeAvailable = recipe("available")
    private val menuFlow = MutableStateFlow<Menu?>(menu())
    private val categoryFlow = MutableStateFlow(listOf(MenuCategory(categoryId, menuId, "Entrees", "entrees", 0)))
    private val placementFlow = MutableStateFlow<List<MenuPlacement>>(emptyList())
    private val recipeFlow = MutableStateFlow<List<MenuRecipe>>(emptyList())
    private val restaurantFlow = MutableStateFlow<Restaurant?>(restaurant())
    private var includeArchivedRequested: Boolean? = null

    private val catalogs = object : MenuCatalogRepository {
        override fun observeMenus(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<Menu>())
        override fun observeMenu(id: MenuId) = menuFlow
        override fun observeCategories(menuId: MenuId) = categoryFlow
        override fun observePlacements(menuId: MenuId) = placementFlow
        override suspend fun createMenu(restaurantId: RestaurantId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal) = menuId
        override suspend fun updateMenu(id: MenuId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal) = Unit
        override suspend fun setArchived(id: MenuId, archived: Boolean) = Unit
        override suspend fun saveCategory(menuId: MenuId, categoryId: MenuCategoryId?, name: String, sortOrder: Int) = this@MenuCatalogDetailViewModelTest.categoryId
        override suspend fun removeCategory(menuId: MenuId, categoryId: MenuCategoryId) = Unit
        override suspend fun reorderCategories(menuId: MenuId, orderedCategoryIds: List<MenuCategoryId>) = Unit
        override suspend fun savePlacement(menuId: MenuId, placementId: MenuPlacementId?, categoryId: MenuCategoryId, menuRecipeId: MenuRecipeId, sortOrder: Int) = MenuPlacementId("new")
        override suspend fun removePlacement(menuId: MenuId, placementId: MenuPlacementId) = Unit
        override suspend fun reorderPlacements(menuId: MenuId, orderedPlacementIds: List<MenuPlacementId>) = Unit
    }
    private val recipes = object : MenuRecipeRepository {
        override fun observeRecipes(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<MenuRecipe>> { includeArchivedRequested = includeArchived; return recipeFlow }
        override fun observeRecipe(id: MenuRecipeId) = flowOf<MenuRecipe?>(null)
        override fun observeComponents(id: MenuRecipeId) = flowOf(emptyList<MenuRecipeComponent>())
        override suspend fun create(restaurantId: RestaurantId, name: String, sellingPrice: BigDecimal?, notes: String?) = MenuRecipeId("new")
        override suspend fun update(id: MenuRecipeId, name: String, sellingPrice: BigDecimal?, notes: String?) = Unit
        override suspend fun setCashDiscountBehavior(id: MenuRecipeId, behavior: CashDiscountBehavior) = Unit
        override suspend fun saveComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId?, ingredientId: IngredientId, optionId: IngredientUnitOptionId, quantityEntered: BigDecimal, sortOrder: Int) = MenuRecipeComponentId("new")
        override suspend fun removeComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId) = Unit
        override suspend fun setArchived(id: MenuRecipeId, archived: Boolean) = Unit
    }
    private val restaurants = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant() = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) = Unit
    }
    private val publications = object : MenuPublicationRepository {
        override fun observePublications(menuId: MenuId) = flowOf(emptyList<MenuPublication>())
        override fun observePublication(publicationId: MenuPublicationId) = flowOf<MenuPublicationSnapshot?>(null)
        override suspend fun getPublication(publicationId: MenuPublicationId): MenuPublicationSnapshot? = null
        override suspend fun publish(menuId: MenuId) = MenuPublicationId("publication")
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `archived placement remains visible and only active unplaced item is available`() = runTest {
        recipeFlow.value = listOf(archived, activeAvailable)
        placementFlow.value = listOf(placement(archived.id))
        val viewModel = viewModel()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        runCurrent()

        assertThat(includeArchivedRequested).isTrue()
        assertThat(viewModel.state.value.categories.single().items.single().recipe).isEqualTo(archived)
        assertThat(viewModel.state.value.availableItems).containsExactly(activeAvailable)
        collection.cancel()
    }

    @Test fun `active placed item is excluded from picker`() = runTest {
        recipeFlow.value = listOf(activePlaced, activeAvailable)
        placementFlow.value = listOf(placement(activePlaced.id))
        val viewModel = viewModel()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        runCurrent()
        assertThat(viewModel.state.value.availableItems).containsExactly(activeAvailable)
        collection.cancel()
    }

    @Test fun `restaurant currency reaches detail state`() = runTest {
        restaurantFlow.value = restaurant(currency = "EUR")
        val viewModel = viewModel()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        runCurrent()
        assertThat(viewModel.state.value.currencyCode).isEqualTo("EUR")
        collection.cancel()
    }

    @Test fun `restaurant ownership mismatch produces load error without currency`() = runTest {
        restaurantFlow.value = restaurant(id = RestaurantId("other"), currency = "EUR")
        val viewModel = viewModel()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        runCurrent()
        assertThat(viewModel.state.value.loadError).isTrue()
        assertThat(viewModel.state.value.menu).isNull()
        assertThat(viewModel.state.value.currencyCode).isEmpty()
        collection.cancel()
    }

    private fun viewModel() = MenuCatalogDetailViewModel(SavedStateHandle(mapOf("menuId" to menuId.value)), catalogs, recipes, restaurants, publications, com.miara.cuentame.core.domain.service.MenuPackageExporter(publications))
    private fun menu() = Menu(menuId, restaurantId, "Dinner", "dinner", null, BigDecimal.ZERO, 0, null, now, now)
    private fun restaurant(id: RestaurantId = restaurantId, currency: String = "USD") = Restaurant(id, "R", currency, "en-US", now, now, null)
    private fun recipe(id: String, archived: Boolean = false) = MenuRecipe(MenuRecipeId(id), restaurantId, id, id, BigDecimal.TEN, null, CashDiscountBehavior.APPLY_DEFAULT, 0, 0, if (archived) now else null, now, now)
    private fun placement(recipeId: MenuRecipeId) = MenuPlacement(MenuPlacementId("placement-${recipeId.value}"), menuId, categoryId, recipeId, 0)
}
