package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.usecase.AddPackageUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.AddStandardUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUseCase
import com.miara.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.PreviewUnitConversionUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultCountUnitUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultPurchaseUnitUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePackageUnitOptionUseCase
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.core.model.inventory.UnitDimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val ingredientFlow = MutableStateFlow<Ingredient?>(null)
    private val optionsFlow = MutableStateFlow<List<IngredientUnitOption>>(emptyList())

    private val fakeIngredientRepository = object : IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>> = MutableStateFlow(emptyList())
        override fun observeIngredient(id: IngredientId): Flow<Ingredient?> = ingredientFlow
        override suspend fun getById(id: IngredientId): Ingredient? = ingredientFlow.value
        override suspend fun updateIngredient(ingredient: Ingredient) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): Flow<List<IngredientUnitOption>> = optionsFlow
        override suspend fun addStandardUnitOption(command: com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand) {}
        override suspend fun addPackageUnitOption(command: com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand) {}
        override suspend fun updatePackageUnitOption(command: com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand) {}
        override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeCategoryRepository = object : IngredientCategoryRepository {
        override fun observeActiveCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = MutableStateFlow(emptyList())
        override fun observeAllCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: IngredientCategoryId): com.miara.cuentame.core.model.ingredient.IngredientCategory? = null
        override suspend fun save(category: com.miara.cuentame.core.model.ingredient.IngredientCategory) {}
        override suspend fun archive(id: IngredientCategoryId, at: Instant) {}
        override suspend fun reorder(ids: List<IngredientCategoryId>) {}
    }

    private val fakeUnitRepository = object : UnitRepository {
        override fun observeAll(): Flow<List<UnitOfMeasure>> = MutableStateFlow(emptyList())
        override fun observeByDimension(dimension: UnitDimension): Flow<List<UnitOfMeasure>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: UnitId): UnitOfMeasure = UnitOfMeasure(id, "Unit", "u", UnitDimension.MASS, BigDecimal.ONE, true, 0)
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
    }

    private lateinit var viewModel: IngredientDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val ingId = "ing_1"
        ingredientFlow.value = Ingredient(IngredientId(ingId), RestaurantId("r1"), "Chicken", "chicken", null, UnitId("lb"), null, null, null, null, true, Instant.now(), Instant.now())
        
        viewModel = IngredientDetailViewModel(
            SavedStateHandle(mapOf("ingredientId" to ingId)),
            fakeIngredientRepository,
            ObserveIngredientUnitOptionsUseCase(fakeIngredientRepository),
            ObserveCompatibleSystemUnitsUseCase(fakeUnitRepository),
            ObserveIngredientCategoriesUseCase(fakeCategoryRepository),
            fakeUnitRepository,
            ArchiveIngredientUseCase(fakeIngredientRepository),
            AddStandardUnitOptionUseCase(fakeIngredientRepository),
            AddPackageUnitOptionUseCase(fakeIngredientRepository),
            UpdatePackageUnitOptionUseCase(fakeIngredientRepository),
            SetDefaultCountUnitUseCase(fakeIngredientRepository),
            SetDefaultPurchaseUnitUseCase(fakeIngredientRepository),
            ArchiveIngredientUnitOptionUseCase(fakeIngredientRepository),
            PreviewUnitConversionUseCase(StandardUnitConverter()),
            timeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads ingredient`() = runTest {
        viewModel.uiState.test {
            // Skip initial loading state if it emits immediately
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            
            assertThat(state.isLoading).isFalse()
            assertThat(state.ingredient?.name).isEqualTo("Chicken")
        }
    }

    @Test
    fun `options are reactive`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().options).isEmpty()
            
            val option = IngredientUnitOption(IngredientUnitOptionId("o1"), IngredientId("ing_1"), "Case", "case", null, BigDecimal("40"), false, false, true, true, Instant.now(), Instant.now())
            optionsFlow.value = listOf(option)
            
            assertThat(awaitItem().options).hasSize(1)
        }
    }
}
