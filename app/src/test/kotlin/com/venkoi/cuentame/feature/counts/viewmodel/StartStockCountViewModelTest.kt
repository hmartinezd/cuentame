package com.venkoi.cuentame.feature.counts.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.domain.repository.StockCountRepository
import com.venkoi.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.InventoryArea
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StartStockCountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val restaurant = Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
    private val area1 = InventoryArea(InventoryAreaId("a1"), RestaurantId("r1"), "Area 1", "area 1", 0, true, Instant.now(), Instant.now())
    
    private var currentRestaurant: Restaurant? = restaurant
    
    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = flowOf(currentRestaurant)
        override suspend fun getRestaurant(): Restaurant? = currentRestaurant
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fakeStockCountRepository = object : StockCountRepository {
        override fun observeCounts(filter: com.venkoi.cuentame.core.domain.repository.StockCountFilter) = flowOf(emptyList<com.venkoi.cuentame.core.domain.repository.StockCountSummary>())
        override fun observeCount(id: StockCountId) = flowOf(null)
        override fun observeCountArea(id: com.venkoi.cuentame.core.common.ids.StockCountAreaId) = flowOf(null)
        override fun observeHasCompletedCount(restaurantId: RestaurantId) = flowOf(false)
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun getItemOrder(areaId: InventoryAreaId) = emptyList<IngredientId>()
        override suspend fun saveItemOrder(areaId: InventoryAreaId, ingredientIds: List<IngredientId>) {}
        override suspend fun start(command: com.venkoi.cuentame.core.domain.repository.StartStockCountCommand): StockCountId = StockCountId("c1")
        override suspend fun updateDraft(command: com.venkoi.cuentame.core.domain.repository.UpdateStockCountDraftCommand) {}
        override suspend fun saveLine(command: com.venkoi.cuentame.core.domain.repository.SaveStockCountLineCommand) =
            com.venkoi.cuentame.core.model.count.StockCountLine(
                com.venkoi.cuentame.core.common.ids.StockCountLineId("l1"), command.countAreaId,
                command.ingredientId, command.ingredientUnitOptionId, command.quantityEntered,
                command.quantityEntered, java.math.BigDecimal.ZERO, command.quantityEntered,
                command.notes, timeProvider.now(), timeProvider.now()
            )
        override suspend fun deleteLine(countId: StockCountId, countAreaId: com.venkoi.cuentame.core.common.ids.StockCountAreaId, lineId: com.venkoi.cuentame.core.common.ids.StockCountLineId) {}
        override suspend fun completeArea(countId: StockCountId, countAreaId: com.venkoi.cuentame.core.common.ids.StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: com.venkoi.cuentame.core.common.ids.StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun findDrift(countId: StockCountId) = emptyList<com.venkoi.cuentame.core.domain.repository.StockCountDriftItem>()
        override suspend fun reconfirmLine(countId: StockCountId, lineId: com.venkoi.cuentame.core.common.ids.StockCountLineId) {}
        override suspend fun voidCount(countId: StockCountId) {}
        override suspend fun getExportRows(countId: StockCountId) = emptyList<com.venkoi.cuentame.core.domain.repository.StockCountExportRow>()
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T12:00:00Z")
    }

    private lateinit var viewModel: StartStockCountViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = StartStockCountViewModel(
            fakeStockCountRepository,
            ObserveInventoryAreasUseCase(object : com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository {
                override fun observeActiveAreas() = flowOf(listOf(area1))
                override fun observeAllAreas() = flowOf(listOf(area1))
                override suspend fun getById(id: InventoryAreaId) = area1
                override suspend fun save(area: InventoryArea) {}
                override suspend fun archive(id: InventoryAreaId, at: Instant) {}
                override suspend fun reorder(ids: List<InventoryAreaId>) {}
            }),
            fakeRestaurantRepository,
            timeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads areas`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertThat(state.availableAreas).hasSize(1)
            assertThat(state.availableAreas[0].name).isEqualTo("Area 1")
        }
    }

    @Test
    fun `area selection works`() = runTest {
        viewModel.onAreaToggle(area1.id)
        assertThat(viewModel.uiState.value.selectedAreaIds).contains(area1.id)
        
        viewModel.onAreaToggle(area1.id)
        assertThat(viewModel.uiState.value.selectedAreaIds).isEmpty()
    }

    @Test
    fun `start count fails with future date`() = runTest {
        val future = timeProvider.now().plusSeconds(3600)
        viewModel.onDateChanged(future)
        assertThat(viewModel.uiState.value.error).isInstanceOf(ValidationError.InvalidCountEffectiveTime::class.java)
    }

    @Test
    fun `start count succeeds with valid input`() = runTest {
        viewModel.onNameChanged("Monthly")
        viewModel.onAreaToggle(area1.id)
        
        viewModel.events.test {
            viewModel.onStart()
            assertThat(awaitItem()).isInstanceOf(StartStockCountEvent.Success::class.java)
        }
    }

    @Test
    fun `overlapping area is disabled`() = runTest {
        // Mock overlapping area
        val fakeRepoWithOverlap = object : StockCountRepository by fakeStockCountRepository {
            override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = setOf(area1.id)
        }
        val vm = StartStockCountViewModel(
            fakeRepoWithOverlap,
            ObserveInventoryAreasUseCase(object : com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository {
                override fun observeActiveAreas() = flowOf(listOf(area1))
                override fun observeAllAreas() = flowOf(listOf(area1))
                override suspend fun getById(id: InventoryAreaId) = area1
                override suspend fun save(area: InventoryArea) {}
                override suspend fun archive(id: InventoryAreaId, at: Instant) {}
                override suspend fun reorder(ids: List<InventoryAreaId>) {}
            }),
            fakeRestaurantRepository,
            timeProvider
        )
        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertThat(state.draftAreaUsage).contains(area1.id)
        }
    }

    @Test
    fun `default count name is stored in state via onDefaultNameChanged`() = runTest {
        viewModel.onDefaultNameChanged("Default Name")
        assertThat(viewModel.uiState.value.name).isEqualTo("Default Name")
        assertThat(viewModel.uiState.value.isNameManuallyEdited).isFalse()
    }

    @Test
    fun `user-edited count name is preserved when default changes`() = runTest {
        viewModel.onNameChanged("User Name")
        assertThat(viewModel.uiState.value.isNameManuallyEdited).isTrue()
        
        viewModel.onDefaultNameChanged("New Default")
        assertThat(viewModel.uiState.value.name).isEqualTo("User Name")
    }

    @Test
    fun `missing restaurant prevents count creation`() = runTest {
        currentRestaurant = null
        val vm = StartStockCountViewModel(
            fakeStockCountRepository,
            ObserveInventoryAreasUseCase(object : com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository {
                override fun observeActiveAreas() = flowOf(listOf(area1))
                override fun observeAllAreas() = flowOf(listOf(area1))
                override suspend fun getById(id: InventoryAreaId) = area1
                override suspend fun save(area: InventoryArea) {}
                override suspend fun archive(id: InventoryAreaId, at: Instant) {}
                override suspend fun reorder(ids: List<InventoryAreaId>) {}
            }),
            fakeRestaurantRepository,
            timeProvider
        )
        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertThat(state.error).isInstanceOf(ValidationError.RestaurantNotFound::class.java)
        }
    }
}
