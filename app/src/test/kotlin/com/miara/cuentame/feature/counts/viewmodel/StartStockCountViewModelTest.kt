package com.miara.cuentame.feature.counts.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.restaurant.Restaurant
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
    
    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = flowOf(restaurant)
        override suspend fun getRestaurant(): Restaurant = restaurant
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fakeStockCountRepository = object : StockCountRepository {
        override fun observeCounts(filter: com.miara.cuentame.core.domain.repository.StockCountFilter) = flowOf(emptyList<com.miara.cuentame.core.domain.repository.StockCountSummary>())
        override fun observeCount(id: StockCountId) = flowOf(null)
        override fun observeCountArea(id: com.miara.cuentame.core.common.ids.StockCountAreaId) = flowOf(null)
        override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId) = emptySet<IngredientId>()
        override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
        override suspend fun start(command: com.miara.cuentame.core.domain.repository.StartStockCountCommand): StockCountId = StockCountId("c1")
        override suspend fun updateDraft(command: com.miara.cuentame.core.domain.repository.UpdateStockCountDraftCommand) {}
        override suspend fun saveLine(command: com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand) = com.miara.cuentame.core.common.ids.StockCountLineId("l1")
        override suspend fun deleteLine(countId: StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId, lineId: com.miara.cuentame.core.common.ids.StockCountLineId) {}
        override suspend fun completeArea(countId: StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId) {}
        override suspend fun reopenArea(countId: StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId) {}
        override suspend fun deleteDraft(countId: StockCountId) {}
        override suspend fun completeCount(countId: StockCountId) {}
        override suspend fun voidCount(countId: StockCountId) {}
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
            ObserveInventoryAreasUseCase(object : com.miara.cuentame.core.domain.repository.InventoryAreaRepository {
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
            ObserveInventoryAreasUseCase(object : com.miara.cuentame.core.domain.repository.InventoryAreaRepository {
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
}
