package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.usecase.*
import com.miara.cuentame.core.domain.service.*
import com.miara.cuentame.core.model.count.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StockCountAreaViewModelRaceTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val restId = RestaurantId("r1")
    private val countId = StockCountId("c1")
    private val areaId = InventoryAreaId("a1")
    private val countAreaId = StockCountAreaId("ca1")
    private val ingId = IngredientId("i1")
    private val now = Instant.parse("2024-01-01T12:00:00Z")

    private class ControllableStockCountRepository : StockCountRepository {
        val saveLineDeferred = CompletableDeferred<StockCountLineId>()
        var saveLineCallCount = 0
        var deleteLineCallCount = 0
        val deleteLineDeferred = CompletableDeferred<Unit>()

        override fun observeCounts(filter: StockCountFilter) = flowOf(emptyList<StockCountSummary>())
        override fun observeCount(id: StockCountId) = flowOf(null)
        override fun observeCountArea(id: StockCountAreaId) = flowOf(StockCountAreaDetails(
            area = StockCountArea(id, StockCountId("c1"), InventoryAreaId("a1"), CountAreaStatus.NOT_STARTED, null, null, 0),
            areaName = "Area 1",
            restaurantId = RestaurantId("r1"),
            countId = StockCountId("c1"),
            countStatus = StockCountStatus.DRAFT,
            effectiveAt = Instant.now(),
            lines = emptyList()
        ))
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun start(command: StartStockCountCommand) = StockCountId("c1")
        override suspend fun updateDraft(command: UpdateStockCountDraftCommand) {}
        
        override suspend fun saveLine(command: SaveStockCountLineCommand): StockCountLineId {
            saveLineCallCount++
            return saveLineDeferred.await()
        }
        
        override suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {
            deleteLineCallCount++
            deleteLineDeferred.await()
        }
        
        override suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun voidCount(countId: StockCountId) {}
    }

    private lateinit var repository: ControllableStockCountRepository
    private lateinit var viewModel: StockCountAreaViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = ControllableStockCountRepository()
        
        val fakeRestaurantRepo = object : RestaurantRepository {
            override fun observeRestaurant() = flowOf(Restaurant(restId, "R1", "USD", "en-US", now, now, null))
            override suspend fun getRestaurant() = Restaurant(restId, "R1", "USD", "en-US", now, now, null)
            override suspend fun save(restaurant: Restaurant) {}
        }

        val fakeIngredientRepo = object : IngredientRepository {
            override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<Ingredient>())
            override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = emptyList<Ingredient>()
            override fun observeIngredient(id: IngredientId) = flowOf(null)
            override suspend fun getById(id: IngredientId) = null
            override suspend fun updateIngredient(command: UpdateIngredientCommand) {}
            override suspend fun archive(id: IngredientId, at: Instant) {}
            override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = flowOf(emptyList<IngredientUnitOption>())
            override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = listOf(
                IngredientUnitOption(IngredientUnitOptionId("o1"), ingredientId, "Pound", "lb", UnitId("lb"), BigDecimal.ONE, true, true, true, true, now, now, null)
            )
            override suspend fun addStandardUnitOption(command: AddStandardUnitOptionCommand) {}
            override suspend fun addPackageUnitOption(command: AddPackageUnitOptionCommand) {}
            override suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand) {}
            override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
            override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
            override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
            override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
        }

        val fakeCategoryRepo = object : IngredientCategoryRepository {
            override fun observeActiveCategories() = flowOf(emptyList<IngredientCategory>())
            override fun observeAllCategories() = flowOf(emptyList<IngredientCategory>())
            override suspend fun getById(id: IngredientCategoryId) = null
            override suspend fun save(category: IngredientCategory) {}
            override suspend fun archive(id: IngredientCategoryId, at: Instant) {}
            override suspend fun reorder(ids: List<IngredientCategoryId>) {}
        }

        val fakeSnapshotService = object : InventorySnapshotService {
            override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                InventorySnapshot(false, BigDecimal.ZERO, null)
            override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                emptyMap<IngredientId, BigDecimal>()
        }

        viewModel = StockCountAreaViewModel(
            SavedStateHandle(mapOf("countId" to countId.value, "countAreaId" to countAreaId.value)),
            repository,
            fakeRestaurantRepo,
            GetMissingCountItemsUseCase(fakeIngredientRepo, repository, fakeSnapshotService),
            PreviewStockCountLineUseCase(fakeSnapshotService),
            fakeIngredientRepo,
            fakeCategoryRepo,
            object : TimeProvider { override fun now() = now }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete during CREATE removes the committed database row`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        // Start CREATE
        viewModel.onQuantityChanged(ingId.value, "10")
        advanceTimeBy(600) // Trigger debounce
        runCurrent()
        
        assertThat(repository.saveLineCallCount).isEqualTo(1)
        
        // Request DELETE while CREATE is active
        viewModel.onConfirmDelete(ingId.value)
        runCurrent()
        
        // Complete CREATE
        repository.saveLineDeferred.complete(StockCountLineId("generated-l1"))
        runCurrent()
        
        // Verify DELETE was called with the generated ID
        assertThat(repository.deleteLineCallCount).isEqualTo(1)
        
        // Complete DELETE
        repository.deleteLineDeferred.complete(Unit)
        runCurrent()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().lineEntries).isEmpty()
        }
    }

    @Test
    fun `flush during CREATE does not create duplicate line`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        // Start CREATE via debounce
        viewModel.onQuantityChanged(ingId.value, "10")
        advanceTimeBy(600)
        runCurrent()
        
        assertThat(repository.saveLineCallCount).isEqualTo(1)
        
        // Call flush while CREATE is active
        val flushJob = launch {
            viewModel.flushPendingSaves()
        }
        runCurrent()
        
        // Complete CREATE
        repository.saveLineDeferred.complete(StockCountLineId("l1"))
        runCurrent()
        
        flushJob.join()
        
        // Verify only one save call happened
        assertThat(repository.saveLineCallCount).isEqualTo(1)
    }
}
