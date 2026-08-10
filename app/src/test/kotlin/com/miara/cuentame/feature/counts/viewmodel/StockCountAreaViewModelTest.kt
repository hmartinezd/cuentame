package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.model.count.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.usecase.*
import com.miara.cuentame.core.domain.service.*
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.count.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val fakeRestaurantRepo = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = flowOf(Restaurant(restId, "R1", "USD", "en-US", now, now, null))
        override suspend fun getRestaurant(): Restaurant = Restaurant(restId, "R1", "USD", "en-US", now, now, null)
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fakeIngredientRepo = object : IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<Ingredient>())
        override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = emptyList<Ingredient>()
        override fun observeIngredient(id: IngredientId) = flowOf(null)
        override suspend fun getById(id: IngredientId): Ingredient? = Ingredient(id, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        override suspend fun getUnitOption(id: IngredientUnitOptionId): IngredientUnitOption? = 
            getUnitOptions(IngredientId("any"), true).find { it.id == id }
        override suspend fun updateIngredient(command: UpdateIngredientCommand) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = flowOf(emptyList<IngredientUnitOption>())
        override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean) = listOf(
            IngredientUnitOption(IngredientUnitOptionId("o1"), ingredientId, "Pound", "lb", UnitId("lb"), BigDecimal.ONE, true, true, true, true, now, now, null),
            IngredientUnitOption(IngredientUnitOptionId("o2"), ingredientId, "Kilo", "kg", UnitId("kg"), BigDecimal("2.2"), true, false, true, true, now, now, null),
            IngredientUnitOption(IngredientUnitOptionId("archived"), ingredientId, "Old", "old", UnitId("lb"), BigDecimal.ONE, true, false, true, false, now, now, null)
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
            fakeRestaurantRepo,
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
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.details?.areaName).isEqualTo("Area 1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid count and countArea IDs produce Ready`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountAreaScreenState.Ready)
        }
    }

    @Test
    fun `missing countId produces InvalidRoute`() = runTest {
        val vm = StockCountAreaViewModel(
            SavedStateHandle(mapOf("countAreaId" to countAreaId.value)),
            fakeRepo,
            fakeRestaurantRepo,
            GetMissingCountItemsUseCase(fakeIngredientRepo, fakeRepo, object : InventorySnapshotService {
                override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = InventorySnapshot(false, BigDecimal.ZERO, null)
                override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = emptyMap<IngredientId, BigDecimal>()
            }),
            PreviewStockCountLineUseCase(object : InventorySnapshotService {
                override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = InventorySnapshot(false, BigDecimal.ZERO, null)
                override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = emptyMap<IngredientId, BigDecimal>()
            }),
            fakeIngredientRepo,
            fakeCategoryRepo,
            timeProvider
        )
        vm.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountAreaScreenState.InvalidRoute)
        }
    }

    @Test
    fun `missing countAreaId produces InvalidRoute`() = runTest {
        val vm = StockCountAreaViewModel(
            SavedStateHandle(mapOf("countId" to countId.value)),
            fakeRepo,
            fakeRestaurantRepo,
            GetMissingCountItemsUseCase(fakeIngredientRepo, fakeRepo, object : InventorySnapshotService {
                override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = InventorySnapshot(false, BigDecimal.ZERO, null)
                override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = emptyMap<IngredientId, BigDecimal>()
            }),
            PreviewStockCountLineUseCase(object : InventorySnapshotService {
                override suspend fun calculateAt(restaurantId: RestaurantId, ingredientId: IngredientId, areaId: InventoryAreaId, effectiveAt: Instant) = InventorySnapshot(false, BigDecimal.ZERO, null)
                override suspend fun calculateAreaBalancesAt(restaurantId: RestaurantId, areaId: InventoryAreaId, effectiveAt: Instant) = emptyMap<IngredientId, BigDecimal>()
            }),
            fakeIngredientRepo,
            fakeCategoryRepo,
            timeProvider
        )
        vm.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountAreaScreenState.InvalidRoute)
        }
    }

    @Test
    fun `area belonging to another count produces OwnershipMismatch`() = runTest {
        detailsFlow.value = detailsFlow.value?.copy(
            area = detailsFlow.value!!.area.copy(stockCountId = StockCountId("other-count"))
        )
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }
            assertThat(state.screenState).isEqualTo(StockCountAreaScreenState.OwnershipMismatch)
        }
    }

    @Test
    fun `Archived unit becomes disabled after changing away`() = runTest {
        val archivedId = IngredientUnitOptionId("archived")
        val activeId = IngredientUnitOptionId("o1")
        
        // Setup initial line with archived unit
        detailsFlow.value = detailsFlow.value?.copy(
            lines = listOf(
                StockCountLine(
                    id = StockCountLineId("l1"),
                    stockCountAreaId = countAreaId,
                    ingredientId = ingId,
                    ingredientUnitOptionId = archivedId,
                    quantityEntered = BigDecimal.TEN,
                    quantityBase = BigDecimal.TEN,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.lineEntries.isEmpty()) {
                state = awaitItem()
            }
            
            val entry = state.lineEntries.first()
            val archivedOption = entry.unitOptions.find { it.id == archivedId }!!
            assertThat(archivedOption.isSelected).isTrue()
            assertThat(archivedOption.isSelectable).isTrue()
            
            viewModel.onUnitChanged(ingId.value, activeId.value)
            
            state = awaitItem()
            while (state.lineEntries.first().unitId != activeId.value) {
                state = awaitItem()
            }
            
            val updatedEntry = state.lineEntries.first()
            val updatedArchivedOption = updatedEntry.unitOptions.find { it.id == archivedId }!!
            assertThat(updatedArchivedOption.isSelected).isFalse()
            assertThat(updatedArchivedOption.isSelectable).isFalse()
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user edit makes line pending`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        
        viewModel.uiState.test {
            viewModel.onQuantityChanged(ingId.value, "10")
            var state = awaitItem()
            while (!state.hasPendingSaves) {
                state = awaitItem()
            }
            assertThat(state.hasPendingSaves).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back navigation flushes pending saves`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        viewModel.onAddIngredient(ingredient)
        runCurrent()
        viewModel.onQuantityChanged(ingId.value, "20")
        runCurrent()
        viewModel.events.test {
            viewModel.onBackRequested()
            assertThat(awaitItem()).isInstanceOf(StockCountAreaEvent.NavigateBack::class.java)
        }
    }

    @Test
    fun `untouched suggestions are not pending`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        val candidateResult = CountCandidateResult(listOf(ingredient), listOf(ingredient), emptyList())
        
        val mockGetMissing = mockk<GetMissingCountItemsUseCase>()
        coEvery { mockGetMissing(any(), any(), any(), any()) } returns candidateResult
        
        val vm = StockCountAreaViewModel(
            SavedStateHandle(mapOf("countId" to countId.value, "countAreaId" to countAreaId.value)),
            fakeRepo,
            fakeRestaurantRepo,
            mockGetMissing,
            PreviewStockCountLineUseCase(mockk(relaxed = true)),
            fakeIngredientRepo,
            fakeCategoryRepo,
            timeProvider
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state.lineEntries.isEmpty()) {
                state = awaitItem()
            }
            
            val entry = state.lineEntries.first()
            assertThat(entry.hasUserEdit).isFalse()
            assertThat(entry.isPending).isFalse()
            assertThat(state.hasPendingSaves).isFalse()
        }
    }

    @Test
    fun `rapid unit selection preserves final selection`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        
        viewModel.uiState.test {
            // Wait for initial load
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }

            viewModel.onAddIngredient(ingredient)
            
            // Wait for ingredient to be added
            state = awaitItem()
            while (state.lineEntries.isEmpty()) {
                state = awaitItem()
            }
            
            viewModel.onQuantityChanged(ingId.value, "10")
            viewModel.onUnitChanged(ingId.value, "o1")
            viewModel.onUnitChanged(ingId.value, "o2")
            
            state = awaitItem()
            while (state.lineEntries.first().unitId != "o2") {
                state = awaitItem()
            }
            assertThat(state.lineEntries.first().unitId).isEqualTo("o2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid input blocks completion`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        
        viewModel.uiState.test {
            // Wait for initial load
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }

            viewModel.onAddIngredient(ingredient)
            
            // Wait for ingredient to be added
            state = awaitItem()
            while (state.lineEntries.isEmpty()) {
                state = awaitItem()
            }
            
            viewModel.onQuantityChanged(ingId.value, "invalid")
            viewModel.onCompleteArea()
            
            state = awaitItem()
            while (state.error == null) {
                state = awaitItem()
            }
            assertThat(state.error).isEqualTo(ValidationError.PendingCountSaves)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stale save result does not overwrite newer state`() = runTest {
        val ingredient = Ingredient(ingId, restId, "Chicken", "chicken", null, UnitId("lb"), areaId, null, null, null, true, now, now, null)
        
        viewModel.uiState.test {
            // Wait for initial load
            var state = awaitItem()
            while (state.screenState == StockCountAreaScreenState.Loading) {
                state = awaitItem()
            }

            viewModel.onAddIngredient(ingredient)
            
            // Wait for ingredient to be added
            state = awaitItem()
            while (state.lineEntries.isEmpty()) {
                state = awaitItem()
            }

            // Revision 1
            viewModel.onQuantityChanged(ingId.value, "10")
            
            // Revision 2 (immediate)
            viewModel.onQuantityChanged(ingId.value, "20")
            
            state = awaitItem()
            while (state.lineEntries.first().quantityText != "20") {
                state = awaitItem()
            }
            
            // Wait for saves to finish
            testDispatcher.scheduler.advanceUntilIdle()
            
            state = awaitItem()
            while (state.hasPendingSaves) {
                state = awaitItem()
            }
            
            assertThat(state.lineEntries.first().quantityText).isEqualTo("20")
            assertThat(state.lineEntries.first().savedRevision).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
