package com.miara.cuentame.feature.ingredients.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val ingredientsFlow = MutableStateFlow<List<Ingredient>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>>(emptyList())
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakeIngredientRepository = object : IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>> = ingredientsFlow
        override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean): List<Ingredient> = ingredientsFlow.value
        override fun observeIngredient(id: IngredientId): Flow<Ingredient?> = MutableStateFlow(null)
        override suspend fun getById(id: IngredientId): Ingredient? = null
        override suspend fun updateIngredient(command: com.miara.cuentame.core.domain.repository.UpdateIngredientCommand) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): Flow<List<IngredientUnitOption>> = MutableStateFlow(emptyList())
        override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): List<IngredientUnitOption> = emptyList()
        override suspend fun addStandardUnitOption(command: com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand) {}
        override suspend fun addPackageUnitOption(command: com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand) {}
        override suspend fun updatePackageUnitOption(command: com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand) {}
        override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeCategoryRepository = object : IngredientCategoryRepository {
        override fun observeActiveCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = categoriesFlow
        override fun observeAllCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = categoriesFlow
        override suspend fun getById(id: IngredientCategoryId): com.miara.cuentame.core.model.ingredient.IngredientCategory? = null
        override suspend fun save(category: com.miara.cuentame.core.model.ingredient.IngredientCategory) {}
        override suspend fun archive(id: IngredientCategoryId, at: Instant) {}
        override suspend fun reorder(ids: List<IngredientCategoryId>) {}
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
    }

    private lateinit var viewModel: IngredientListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val observeIngredientsUseCase = ObserveIngredientsUseCase(fakeIngredientRepository)
        val observeCategoriesUseCase = ObserveIngredientCategoriesUseCase(fakeCategoryRepository)
        restaurantFlow.value = Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
        viewModel = IngredientListViewModel(observeIngredientsUseCase, observeCategoriesUseCase, fakeRestaurantRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search filters ingredients with normalization`() = runTest {
        val ing1 = createIngredient("Chicken Breast")
        val ing2 = createIngredient("Beef")
        ingredientsFlow.value = listOf(ing1, ing2)
        
        viewModel.uiState.test {
            // Skip initial loading if present
            var item = awaitItem()
            if (item.isLoading) {
                advanceTimeBy(301)
                item = awaitItem()
            }
            assertThat(item.ingredients).hasSize(2)
            
            viewModel.onSearchQueryChanged("  chicken   breast ")
            advanceTimeBy(301)
            val filtered = awaitItem().ingredients
            assertThat(filtered).hasSize(1)
            assertThat(filtered.first().name).isEqualTo("Chicken Breast")
        }
    }

    @Test
    fun `search filters ingredients case-insensitive`() = runTest {
        val ing1 = createIngredient("Chicken")
        ingredientsFlow.value = listOf(ing1)
        
        viewModel.uiState.test {
            // Skip initial loading
            var item = awaitItem()
            if (item.isLoading) {
                advanceTimeBy(301)
                item = awaitItem()
            }
            assertThat(item.ingredients).hasSize(1)
            
            viewModel.onSearchQueryChanged("CHICKEN")
            advanceTimeBy(301)
            assertThat(awaitItem().ingredients).hasSize(1)
        }
    }

    @Test
    fun `category filter filters ingredients`() = runTest {
        val catId = IngredientCategoryId("c1")
        val ing1 = createIngredient("Chicken").copy(categoryId = catId)
        val ing2 = createIngredient("Beef")
        ingredientsFlow.value = listOf(ing1, ing2)

        viewModel.uiState.test {
            // Skip initial loading
            var item = awaitItem()
            if (item.isLoading) {
                advanceTimeBy(301)
                item = awaitItem()
            }
            assertThat(item.ingredients).hasSize(2)

            viewModel.onCategoryFilterChanged(IngredientCategoryFilter.Category(catId))
            val filtered = awaitItem().ingredients
            assertThat(filtered).hasSize(1)
            assertThat(filtered.first().name).isEqualTo("Chicken")
            
            viewModel.onCategoryFilterChanged(IngredientCategoryFilter.Uncategorized)
            val uncategorized = awaitItem().ingredients
            assertThat(uncategorized).hasSize(1)
            assertThat(uncategorized.first().name).isEqualTo("Beef")
        }
    }

    @Test
    fun `archived toggle updates includeArchived flag`() = runTest {
        var observedIncludeArchived = false
        val customRepo = object : IngredientRepository by fakeIngredientRepository {
            override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>> {
                observedIncludeArchived = includeArchived
                return ingredientsFlow
            }
        }
        
        val vm = IngredientListViewModel(
            ObserveIngredientsUseCase(customRepo),
            ObserveIngredientCategoriesUseCase(fakeCategoryRepository),
            fakeRestaurantRepository
        )
        
        // Trigger collection to activate flatMapLatest
        val job = launch { vm.uiState.collect {} }
        runCurrent()
        
        vm.onShowArchivedToggled(true)
        runCurrent()
        assertThat(observedIncludeArchived).isTrue()
        
        vm.onShowArchivedToggled(false)
        runCurrent()
        assertThat(observedIncludeArchived).isFalse()
        
        job.cancel()
    }

    private fun createIngredient(name: String) = Ingredient(
        id = IngredientId(name),
        restaurantId = RestaurantId("r1"),
        name = name,
        normalizedName = name.lowercase(),
        categoryId = null,
        baseUnitId = UnitId("u1"),
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
