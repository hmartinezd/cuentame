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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
        val detailsFlow = MutableStateFlow(StockCountAreaDetails(
            area = StockCountArea(StockCountAreaId("ca1"), StockCountId("c1"), InventoryAreaId("a1"), CountAreaStatus.NOT_STARTED, null, null, 0),
            areaName = "Area 1",
            restaurantId = RestaurantId("r1"),
            countId = StockCountId("c1"),
            countStatus = StockCountStatus.DRAFT,
            effectiveAt = Instant.parse("2024-01-01T12:00:00Z"),
            lines = emptyList()
        ))
        val saveLineDeferreds = mutableListOf<CompletableDeferred<StockCountLine>>()
        val deleteLineDeferreds = mutableListOf<CompletableDeferred<Unit>>()
        
        val savedCommands = mutableListOf<SaveStockCountLineCommand>()
        val deletedLineIds = mutableListOf<StockCountLineId>()

        override fun observeCounts(filter: StockCountFilter) = flowOf(emptyList<StockCountSummary>())
        override fun observeCount(id: StockCountId) = flowOf(null)
        override fun observeCountArea(id: StockCountAreaId) = detailsFlow
        override fun observeHasCompletedCount(restaurantId: RestaurantId) = flowOf(false)
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun getItemOrder(areaId: InventoryAreaId) = emptyList<IngredientId>()
        override suspend fun saveItemOrder(areaId: InventoryAreaId, ingredientIds: List<IngredientId>) {}
        override suspend fun start(command: StartStockCountCommand) = StockCountId("c1")
        override suspend fun updateDraft(command: UpdateStockCountDraftCommand) {}
        
        override suspend fun saveLine(command: SaveStockCountLineCommand): StockCountLine {
            savedCommands.add(command)
            val deferred = CompletableDeferred<StockCountLine>()
            saveLineDeferreds.add(deferred)
            return deferred.await()
        }
        
        override suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {
            deletedLineIds.add(lineId)
            val deferred = CompletableDeferred<Unit>()
            deleteLineDeferreds.add(deferred)
            deferred.await()
        }
        
        override suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun findDrift(countId: StockCountId) = emptyList<StockCountDriftItem>()
        override suspend fun reconfirmLine(countId: StockCountId, lineId: StockCountLineId) {}
        override suspend fun voidCount(countId: StockCountId) {}
    }

    private lateinit var repository: ControllableStockCountRepository
    private lateinit var viewModel: StockCountAreaViewModel

    private fun savedLine(index: Int, id: String = "l1", expected: String = "0"):
        StockCountLine {
        val command = repository.savedCommands[index]
        val expectedValue = BigDecimal(expected)
        return StockCountLine(
            StockCountLineId(id), command.countAreaId, command.ingredientId,
            command.ingredientUnitOptionId, command.quantityEntered, command.quantityEntered,
            expectedValue, command.quantityEntered.subtract(expectedValue), command.notes, now, now
        )
    }

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
            override suspend fun getUnitOption(id: IngredientUnitOptionId): IngredientUnitOption? = null
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
                InventorySnapshot(true, BigDecimal("20"), null)
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
    fun `Delete during CREATE captures generated ID and removes line`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        // 1. Start CREATE
        viewModel.onQuantityChanged(ingId.value, "10")
        advanceTimeBy(600) // Trigger debounce
        runCurrent()
        
        assertThat(repository.savedCommands).hasSize(1)
        assertThat(repository.savedCommands[0].lineId).isNull()
        
        // 2. Request DELETE while CREATE is active
        viewModel.onConfirmDelete(ingId.value)
        runCurrent()
        
        // 3. Complete CREATE
        val generatedId = StockCountLineId("generated-l1")
        repository.saveLineDeferreds[0].complete(savedLine(0, generatedId.value))
        runCurrent()
        
        // 4. Verify DELETE was called with the generated ID
        assertThat(repository.deletedLineIds).containsExactly(generatedId)
        
        // 5. Complete DELETE
        repository.deleteLineDeferreds[0].complete(Unit)
        runCurrent()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().lineEntries).isEmpty()
        }
    }

    @Test
    fun `Queued save and delete complete in order without deadlock`() = runTest(timeout = 5000.toDuration(DurationUnit.MILLISECONDS)) {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        // 1. Save A holds the coordinator
        viewModel.onQuantityChanged(ingId.value, "10")
        advanceTimeBy(600)
        runCurrent()
        assertThat(repository.savedCommands).hasSize(1)
        
        // 2. Save B is queued (via rapid edits or flush)
        viewModel.onQuantityChanged(ingId.value, "20")
        // We bypass debounce and go straight to coordinator via flush to force queuing
        val flushJob = launch { viewModel.flushPendingSaves() }
        runCurrent()
        
        // 3. Delete is queued
        val deleteJob = launch { viewModel.onConfirmDelete(ingId.value) }
        runCurrent()
        
        // 4. Save A completes
        repository.saveLineDeferreds[0].complete(savedLine(0))
        runCurrent()
        
        // 5. Save B should start and then complete
        assertThat(repository.savedCommands).hasSize(2)
        repository.saveLineDeferreds[1].complete(savedLine(1))
        runCurrent()
        
        // 6. Delete should start and then complete
        assertThat(repository.deletedLineIds).containsExactly(StockCountLineId("l1"))
        repository.deleteLineDeferreds[0].complete(Unit)
        runCurrent()
        
        flushJob.join()
        deleteJob.join()
        
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().lineEntries).isEmpty()
        }
    }

    @Test
    fun `Flush during CREATE does not create duplicate line`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        // Start CREATE via debounce
        viewModel.onQuantityChanged(ingId.value, "10")
        advanceTimeBy(600)
        runCurrent()
        
        assertThat(repository.savedCommands).hasSize(1)
        
        // Call flush while CREATE is active
        val flushJob = launch {
            viewModel.flushPendingSaves()
        }
        runCurrent()
        
        // Complete CREATE
        repository.saveLineDeferreds[0].complete(savedLine(0))
        runCurrent()
        
        flushJob.join()
        
        // Verify only one save call happened (no new Save enqueued because revision matched)
        assertThat(repository.savedCommands).hasSize(1)
    }

    @Test
    fun `older save updates persisted baseline without overwriting newer edit`() = runTest {
        val stateCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()

        viewModel.onQuantityChanged(ingId.value, "8")
        advanceTimeBy(600)
        runCurrent()
        assertThat(repository.savedCommands).hasSize(1)

        viewModel.onQuantityChanged(ingId.value, "9")
        runCurrent()
        repository.saveLineDeferreds[0].complete(savedLine(0, expected = "25"))
        runCurrent()

        var current = viewModel.uiState.value.lineEntries.single()
        assertThat(current.quantityText).isEqualTo("9")
        assertThat(current.isPending).isTrue()
        assertThat(current.persistedPreview?.expectedQuantityBase).isEqualTo(BigDecimal("25"))
        assertThat(current.preview?.expectedQuantityBase).isEqualTo(BigDecimal("20"))

        advanceTimeBy(600)
        runCurrent()
        assertThat(repository.savedCommands).hasSize(2)
        repository.saveLineDeferreds[1].complete(savedLine(1, expected = "30"))
        runCurrent()

        current = viewModel.uiState.value.lineEntries.single()
        assertThat(current.quantityText).isEqualTo("9")
        assertThat(current.isSaved).isTrue()
        assertThat(current.preview?.expectedQuantityBase).isEqualTo(BigDecimal("30"))
        assertThat(current.preview?.provisionalAdjustmentBase).isEqualTo(BigDecimal("-21"))
        stateCollection.cancel()
    }

    @Test
    fun `Room emission before save continuation converges on authoritative persisted preview`() = runTest {
        val stateCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        viewModel.onQuantityChanged(ingId.value, "9")
        advanceTimeBy(600)
        runCurrent()

        val persisted = savedLine(0, expected = "25").copy(updatedAt = now.plusSeconds(1))
        repository.detailsFlow.value = repository.detailsFlow.value.copy(lines = listOf(persisted))
        runCurrent()
        assertThat(viewModel.uiState.value.lineEntries.single().isPending).isTrue()
        assertThat(viewModel.uiState.value.lineEntries.single().persistedPreview?.expectedQuantityBase)
            .isEqualTo(BigDecimal("25"))

        repository.saveLineDeferreds[0].complete(persisted)
        runCurrent()
        val stable = viewModel.uiState.value.lineEntries.single()
        assertThat(stable.isSaved).isTrue()
        assertThat(stable.preview?.expectedQuantityBase).isEqualTo(BigDecimal("25"))
        assertThat(stable.preview?.provisionalAdjustmentBase).isEqualTo(BigDecimal("-16"))
        stateCollection.cancel()
    }
}
