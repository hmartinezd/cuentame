package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.usecase.*
import com.miara.cuentame.core.domain.service.*
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.count.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.inventory.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StockCountAreaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val restId = RestaurantId("r1")
    private val countId = StockCountId("c1")
    private val areaId = InventoryAreaId("a1")
    private val countAreaId = StockCountAreaId("ca1")
    private val ingId = IngredientId("i1")
    private val now = Instant.parse("2024-01-01T12:00:00Z")

    private val detailsFlow = MutableStateFlow<StockCountAreaDetails?>(null)
    
    private val fakeRepo = object : StockCountRepository {
        override fun observeCounts(filter: StockCountFilter) = flowOf(emptyList<StockCountSummary>())
        override fun observeCount(id: StockCountId) = flowOf(null)
        override fun observeCountArea(id: StockCountAreaId) = detailsFlow
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun start(command: StartStockCountCommand) = countId
        override suspend fun updateDraft(command: UpdateStockCountDraftCommand) {}
        override suspend fun saveLine(command: SaveStockCountLineCommand) = StockCountLineId("l1")
        override suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {}
        override suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun voidCount(countId: StockCountId) {}
    }

    private val fakeIngredientRepo = object : IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<Ingredient>())
        override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = emptyList<Ingredient>()
        override fun observeIngredient(id: IngredientId) = flowOf(null)
        override suspend fun getById(id: IngredientId): Ingredient? = Ingredient(id, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        override suspend fun updateIngredient(command: UpdateIngredientCommand) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = flowOf(emptyList<IngredientUnitOption>())
        override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = listOf(
            IngredientUnitOption(IngredientUnitOptionId("o1"), ingredientId, "Pound", "lb", UnitId("lb"), BigDecimal.ONE, true, true, true, true, now, now)
        )
        override suspend fun addStandardUnitOption(command: AddStandardUnitOptionCommand) {}
        override suspend fun addPackageUnitOption(command: AddPackageUnitOptionCommand) {}
        override suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand) {}
        override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeCategoryRepo = object : IngredientCategoryRepository {
        override fun observeActiveCategories() = flowOf(emptyList<IngredientCategory>())
        override fun observeAllCategories() = flowOf(emptyList<IngredientCategory>())
        override suspend fun getById(id: IngredientCategoryId): IngredientCategory? = null
        override suspend fun save(category: IngredientCategory) {}
        override suspend fun archive(id: IngredientCategoryId, at: Instant) {}
        override suspend fun reorder(ids: List<IngredientCategoryId>) {}
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = now
    }

    private lateinit var viewModel: StockCountAreaViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        detailsFlow.value = StockCountAreaDetails(
            area = StockCountArea(countAreaId, countId, areaId, CountAreaStatus.NOT_STARTED, null, null, 0),
            areaName = "Area 1",
            restaurantId = restId,
            countId = countId,
            countStatus = StockCountStatus.DRAFT,
            effectiveAt = now,
            lines = emptyList()
        )

        val fakeSnapshotService = object : InventorySnapshotService {
            override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                InventorySnapshot(false, BigDecimal.ZERO, null)
            override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                emptyMap<IngredientId, BigDecimal>()
        }

        viewModel = StockCountAreaViewModel(
            SavedStateHandle(mapOf("countId" to countId.value, "countAreaId" to countAreaId.value)),
            fakeRepo,
            GetMissingCountItemsUseCase(fakeIngredientRepo, fakeRepo, fakeSnapshotService),
            PreviewStockCountLineUseCase(fakeSnapshotService),
            fakeIngredientRepo,
            fakeCategoryRepo,
            timeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads correctly`() = runTest {
        viewModel.uiState.test {
            runCurrent()
            assertThat(expectMostRecentItem().details?.areaName).isEqualTo("Area 1")
        }
    }

    @Test
    fun `untouched suggestions are not pending`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        viewModel.uiState.test {
            runCurrent()
            viewModel.onAddIngredient(ingredient)
            runCurrent()
            val state = expectMostRecentItem()
            assertThat(state.lineEntries).hasSize(1)
            assertThat(state.hasPendingSaves).isFalse()
        }
    }

    @Test
    fun `user edit makes line pending`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        viewModel.uiState.test {
            runCurrent()
            viewModel.onAddIngredient(ingredient)
            runCurrent()
            viewModel.onQuantityChanged(ingId.value, "10")
            runCurrent()
            val state = expectMostRecentItem()
            assertThat(state.hasPendingSaves).isTrue()
        }
    }

    @Test
    fun `invalid input blocks completion`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        viewModel.uiState.test {
            runCurrent()
            viewModel.onAddIngredient(ingredient)
            runCurrent()
            viewModel.onQuantityChanged(ingId.value, "invalid")
            runCurrent()
            viewModel.onCompleteArea()
            runCurrent()
            val state = expectMostRecentItem()
            assertThat(state.error).isInstanceOf(ValidationError.PendingCountSaves::class.java)
        }
    }

    @Test
    fun `back navigation flushes pending saves`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        viewModel.onQuantityChanged(ingId.value, "20")
        runCurrent()
        viewModel.events.test {
            viewModel.onBackRequested()
            runCurrent()
            assertThat(awaitItem()).isInstanceOf(StockCountAreaEvent.NavigateBack::class.java)
        }
    }

    @Test
    fun `stale save result does not overwrite newer state`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now)
        viewModel.uiState.test {
            runCurrent()
            viewModel.onAddIngredient(ingredient)
            runCurrent()
            viewModel.onQuantityChanged(ingId.value, "10")
            runCurrent()
            viewModel.onQuantityChanged(ingId.value, "20")
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            val state = expectMostRecentItem()
            assertThat(state.lineEntries.find { it.ingredientId == ingId.value }?.quantityText).isEqualTo("20")
        }
    }
}
