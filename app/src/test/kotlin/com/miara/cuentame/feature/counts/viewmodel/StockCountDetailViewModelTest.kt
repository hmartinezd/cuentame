package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.usecase.*
import com.miara.cuentame.core.domain.service.*
import com.miara.cuentame.core.model.count.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StockCountDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val restId = RestaurantId("r1")
    private val countId = StockCountId("c1")
    private val now = Instant.parse("2024-01-01T12:00:00Z")

    private val detailsFlow = MutableStateFlow<StockCountDetails?>(null)
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)
    
    private val fakeRepo = object : StockCountRepository {
        override fun observeCounts(filter: StockCountFilter) = flowOf(emptyList<StockCountSummary>())
        override fun observeCount(id: StockCountId) = detailsFlow
        override fun observeCountArea(id: StockCountAreaId) = flowOf(null)
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun getItemOrder(areaId: InventoryAreaId) = emptyList<IngredientId>()
        override suspend fun saveItemOrder(areaId: InventoryAreaId, ingredientIds: List<IngredientId>) {}
        override suspend fun start(command: StartStockCountCommand) = countId
        override suspend fun updateDraft(command: UpdateStockCountDraftCommand) {}
        override suspend fun saveLine(command: SaveStockCountLineCommand) = StockCountLine(
            StockCountLineId("l1"), command.countAreaId, command.ingredientId,
            command.ingredientUnitOptionId, command.quantityEntered, command.quantityEntered,
            BigDecimal.ZERO, command.quantityEntered, command.notes, now, now
        )
        override suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {}
        override suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun findDrift(countId: StockCountId) = emptyList<StockCountDriftItem>()
        override suspend fun reconfirmLine(countId: StockCountId, lineId: StockCountLineId) {}
        override suspend fun voidCount(countId: StockCountId) {}
    }

    private val fakeIngredientRepo = object : IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<Ingredient>())
        override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = emptyList<Ingredient>()
        override fun observeIngredient(id: IngredientId) = flowOf(null)
        override suspend fun getById(id: IngredientId): Ingredient? = Ingredient(
            id, restId, "Beef", "beef", null, UnitId("lb"), null,
            null, null, null, true, now, now, null
        )
        override suspend fun getUnitOption(id: IngredientUnitOptionId): IngredientUnitOption? =
            getUnitOptions(IngredientId("i1"), true).find { it.id == id }
        override suspend fun updateIngredient(command: UpdateIngredientCommand) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = flowOf(emptyList<IngredientUnitOption>())
        override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = listOf(
            IngredientUnitOption(
                IngredientUnitOptionId("o1"), ingredientId, "Pound", "lb", UnitId("lb"),
                BigDecimal.ONE, true, true, true, true, now, now, null
            )
        )
        override suspend fun addStandardUnitOption(command: AddStandardUnitOptionCommand) {}
        override suspend fun addPackageUnitOption(command: AddPackageUnitOptionCommand) {}
        override suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand) {}
        override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeRestaurantRepo = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant = restaurantFlow.value!!
        override suspend fun save(restaurant: Restaurant) {}
    }

    private lateinit var viewModel: StockCountDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        restaurantFlow.value = Restaurant(restId, "R1", "USD", "en-US", now, now, null)
        detailsFlow.value = StockCountDetails(
            count = StockCount(countId, restId, "Monthly", now, now, null, StockCountStatus.DRAFT, null, now, now, null),
            areas = emptyList()
        )

        val fakeSnapshotService = object : InventorySnapshotService {
            override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                InventorySnapshot(false, BigDecimal.ZERO, null)
            override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = 
                emptyMap<IngredientId, BigDecimal>()
        }

        viewModel = StockCountDetailViewModel(
            SavedStateHandle(mapOf("countId" to countId.value)),
            fakeRepo,
            fakeIngredientRepo,
            fakeRestaurantRepo,
            GetMissingCountItemsUseCase(fakeIngredientRepo, fakeRepo, fakeSnapshotService),
            PreviewStockCountLineUseCase(fakeSnapshotService)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `review uses the same persisted snapshot as stable area row`() = runTest {
        val countAreaId = StockCountAreaId("ca1")
        val line = StockCountLine(
            StockCountLineId("l1"), countAreaId, IngredientId("i1"),
            IngredientUnitOptionId("o1"), BigDecimal("9"), BigDecimal("9"),
            BigDecimal("25"), BigDecimal("-16"), null, now, now
        )
        detailsFlow.value = detailsFlow.value!!.copy(
            areas = listOf(
                StockCountAreaDetails(
                    StockCountArea(countAreaId, countId, InventoryAreaId("a1"), CountAreaStatus.IN_PROGRESS, now, null, 0),
                    "Walk-in", restId, countId, StockCountStatus.DRAFT, now, listOf(line)
                )
            )
        )
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        runCurrent()
        viewModel.onToggleReview(true)
        runCurrent()

        val review = viewModel.uiState.value.reviewLines.single()
        assertThat(review.quantityBase).isEqualTo(line.quantityBase)
        assertThat(review.preview.expectedQuantityBase).isEqualTo(line.expectedQuantityBaseSnapshot)
        assertThat(review.preview.provisionalAdjustmentBase).isEqualTo(line.adjustmentQuantityBase)
        collection.cancel()
    }

    @Test
    fun `initial state loads correctly`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountDetailScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.details?.count?.name).isEqualTo("Monthly")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ownership mismatch state works`() = runTest {
        restaurantFlow.value = Restaurant(RestaurantId("other"), "Other", "USD", "en-US", now, now, null)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountDetailScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountDetailScreenState.OwnershipMismatch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `not found state works`() = runTest {
        detailsFlow.value = null
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountDetailScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountDetailScreenState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `complete count resets state after success`() = runTest {
        viewModel.onComplete()
        runCurrent()
        assertThat(viewModel.uiState.value.isCompleting).isFalse()
    }
}
