package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.usecase.CreateIngredientUseCase
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.PreviewUnitConversionUseCase
import com.miara.cuentame.core.domain.usecase.UpdateIngredientUseCase
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeIngredientRepository = object : IngredientRepository {
        override fun observeActiveIngredients(): Flow<List<Ingredient>> = MutableStateFlow(emptyList())
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
        override fun observeActiveCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = MutableStateFlow(emptyList())
        override fun observeAllCategories(): Flow<List<com.miara.cuentame.core.model.ingredient.IngredientCategory>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: com.miara.cuentame.core.common.ids.IngredientCategoryId): com.miara.cuentame.core.model.ingredient.IngredientCategory? = null
        override suspend fun save(category: com.miara.cuentame.core.model.ingredient.IngredientCategory) {}
        override suspend fun archive(id: com.miara.cuentame.core.common.ids.IngredientCategoryId, at: Instant) {}
        override suspend fun reorder(ids: List<com.miara.cuentame.core.common.ids.IngredientCategoryId>) {}
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<com.miara.cuentame.core.model.restaurant.Restaurant?> = MutableStateFlow(null)
        override suspend fun getRestaurant(): com.miara.cuentame.core.model.restaurant.Restaurant? = com.miara.cuentame.core.model.restaurant.Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
        override suspend fun save(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) {}
    }

    private val fakeUnitRepository = object : UnitRepository {
        override fun observeAll(): Flow<List<UnitOfMeasure>> = MutableStateFlow(emptyList())
        override fun observeByDimension(dimension: UnitDimension): Flow<List<UnitOfMeasure>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: UnitId): UnitOfMeasure? = null
    }

    private var idCounter = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id_${++idCounter}"
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
    }

    private lateinit var viewModel: IngredientFormViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val observeCategoriesUseCase = ObserveIngredientCategoriesUseCase(fakeCategoryRepository)
        val observeCompatibleUnitsUseCase = ObserveCompatibleSystemUnitsUseCase(fakeUnitRepository)
        val getDetailUseCase = GetIngredientDetailUseCase(fakeIngredientRepository)
        val createUseCase = CreateIngredientUseCase(fakeIngredientRepository)
        val updateUseCase = UpdateIngredientUseCase(fakeIngredientRepository)
        val observeOptionsUseCase = ObserveIngredientUnitOptionsUseCase(fakeIngredientRepository)
        val previewUseCase = PreviewUnitConversionUseCase(StandardUnitConverter())

        viewModel = IngredientFormViewModel(
            SavedStateHandle(),
            getDetailUseCase,
            observeOptionsUseCase,
            observeCategoriesUseCase,
            observeCompatibleUnitsUseCase,
            createUseCase,
            updateUseCase,
            previewUseCase,
            fakeRestaurantRepository,
            fakeUnitRepository,
            idGenerator,
            timeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is not loading`() = runTest {
        runCurrent()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `dimension selection resets base unit`() = runTest {
        runCurrent()
        viewModel.onDimensionSelected(UnitDimension.MASS)
        val unit = UnitOfMeasure(UnitId("lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal.ONE, true, 0)
        viewModel.onBaseUnitSelected(unit)
        
        assertThat(viewModel.uiState.value.selectedBaseUnitId).isEqualTo(UnitId("lb"))
        
        viewModel.onDimensionSelected(UnitDimension.VOLUME)
        assertThat(viewModel.uiState.value.selectedBaseUnitId).isNull()
    }
}
