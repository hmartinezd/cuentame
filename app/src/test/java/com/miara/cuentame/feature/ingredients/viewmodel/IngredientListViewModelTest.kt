package com.miara.cuentame.feature.ingredients.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val fakeIngredientRepository = object : IngredientRepository {
        override fun observeActiveIngredients(): Flow<List<Ingredient>> = ingredientsFlow
        override fun observeIngredient(id: IngredientId): Flow<Ingredient?> = MutableStateFlow(null)
        override suspend fun getById(id: IngredientId): Ingredient? = null
        override suspend fun updateIngredient(ingredient: Ingredient) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId): Flow<List<IngredientUnitOption>> = MutableStateFlow(emptyList())
        override suspend fun saveUnitOption(option: IngredientUnitOption) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeCategoryRepository = object : IngredientCategoryRepository {
        override fun observeActiveCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = categoriesFlow
        override fun observeAllCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = categoriesFlow
        override suspend fun getById(id: com.miara.cuentame.core.common.ids.IngredientCategoryId): com.miara.cuentame.core.model.ingredient.IngredientCategory? = null
        override suspend fun save(category: com.miara.cuentame.core.model.ingredient.IngredientCategory) {}
        override suspend fun archive(id: com.miara.cuentame.core.common.ids.IngredientCategoryId, at: Instant) {}
        override suspend fun reorder(ids: List<com.miara.cuentame.core.common.ids.IngredientCategoryId>) {}
    }

    private lateinit var viewModel: IngredientListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val observeIngredientsUseCase = ObserveIngredientsUseCase(fakeIngredientRepository)
        val observeCategoriesUseCase = ObserveIngredientCategoriesUseCase(fakeCategoryRepository)
        viewModel = IngredientListViewModel(observeIngredientsUseCase, observeCategoriesUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search filters ingredients`() = runTest {
        val ing1 = createIngredient("Chicken")
        val ing2 = createIngredient("Beef")
        
        viewModel.uiState.test {
            // Initial empty emission
            assertThat(awaitItem().ingredients).isEmpty()
            
            ingredientsFlow.value = listOf(ing1, ing2)
            assertThat(awaitItem().ingredients).hasSize(2)
            
            viewModel.onSearchQueryChanged("chi")
            // No new emission yet because of debounce
            
            advanceTimeBy(301)
            val filtered = awaitItem().ingredients
            assertThat(filtered).hasSize(1)
            assertThat(filtered.first().name).isEqualTo("Chicken")
        }
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
