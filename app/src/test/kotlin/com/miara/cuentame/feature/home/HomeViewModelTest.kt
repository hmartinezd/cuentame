package com.miara.cuentame.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val dashboardRepository = mockk<DashboardRepository>()
    
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)
    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns restaurantFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then SetupRequired when no restaurant`() = runTest {
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(HomeScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(HomeScreenState.SetupRequired)
        }
    }

    @Test
    fun `Ready state when restaurant and dashboard success`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot()
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            
            val state = item as HomeScreenState.Ready
            assertThat(state.restaurantName).isEqualTo("Test Rest")
            assertThat(state.currencyCode).isEqualTo("USD")
            assertThat(state.localeTag).isEqualTo("en-US")
            assertThat(state.dashboard.inventoryValue).isEqualTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `Error state when repository fails`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flow {
            throw RuntimeException("Fail")
        }

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            
            val state = item as HomeScreenState.Error
            assertThat(state.cause).isInstanceOf(RuntimeException::class.java)
        }
    }

    @Test
    fun `stale range result cannot overwrite newer range`() = runTest {
        restaurantFlow.value = restaurant
        
        val flow30 = MutableSharedFlow<DashboardSnapshot>()
        val flow7 = MutableSharedFlow<DashboardSnapshot>()
        
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Emit 30-day snapshot
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 30))
            var item = awaitItem()
            while (item is HomeScreenState.Loading) item = awaitItem()
            val initialReady = item as HomeScreenState.Ready
            assertThat(initialReady.selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(initialReady.dashboard.negativeBalanceCount).isEqualTo(30)
            
            // 2. Switch to 7 days
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Should emit Ready(isRefreshing = true) instead of Loading
            val refreshingItem = awaitItem() as HomeScreenState.Ready
            assertThat(refreshingItem.isRefreshing).isTrue()
            assertThat(refreshingItem.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(refreshingItem.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 3. Emit 7-day snapshot
            flow7.emit(createEmptySnapshot().copy(negativeBalanceCount = 7))
            val newItem = awaitItem() as HomeScreenState.Ready
            assertThat(newItem.isRefreshing).isFalse()
            assertThat(newItem.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(newItem.loadedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(newItem.dashboard.negativeBalanceCount).isEqualTo(7)
            
            // 4. Emit late 30-day snapshot - SHOULD BE IGNORED
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 999))
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Verify state is still 7 days
            val finalState = viewModel.uiState.value as HomeScreenState.Ready
            assertThat(finalState.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(finalState.dashboard.negativeBalanceCount).isEqualTo(7)
            
            expectNoEvents()
        }
    }

    @Test
    fun `refresh failure keeps previous data and shows error`() = runTest {
        restaurantFlow.value = restaurant
        
        val flow30 = MutableSharedFlow<DashboardSnapshot>()
        val flow7 = flow<DashboardSnapshot> { throw RuntimeException("Refresh fail") }
        
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Successful initial load
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 30))
            awaitItem() // Ready
            
            // 2. Switch range (triggers refresh)
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat((awaitItem() as HomeScreenState.Ready).isRefreshing).isTrue()
            
            // 3. New range fails
            val finalItem = awaitItem() as HomeScreenState.Ready
            assertThat(finalItem.isRefreshing).isFalse()
            assertThat(finalItem.refreshError).isTrue()
            // Previous data should still be there
            assertThat(finalItem.dashboard.negativeBalanceCount).isEqualTo(30)
            assertThat(finalItem.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(finalItem.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }

    @Test
    fun `coverage mapping handles zero stocked ingredients`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal.ZERO, 0, 0, 0)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            
            val dashboard = item.dashboard
            assertThat(dashboard.stockedIngredientCount).isEqualTo(0)
            assertThat(dashboard.costCoverage).isNull()
        }
    }

    @Test
    fun `comparison mapping NEW`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, null)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.NEW)
        }
    }

    @Test
    fun `change range to LAST_90_DAYS`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(createEmptySnapshot())
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        
        viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_90_DAYS)
    }

    @Test
    fun `retry resubscribes after failure`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { dashboardRepository.observeDashboard(any(), any()) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("First fail") }
            else flowOf(createEmptySnapshot())
        }

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isInstanceOf(HomeScreenState.Error::class.java)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(HomeScreenState.Loading)
            assertThat(awaitItem()).isInstanceOf(HomeScreenState.Ready::class.java)
        }
    }

    @Test
    fun `comparison mapping INCREASE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("150"), BigDecimal("100"), BigDecimal("50"), BigDecimal("50"))
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.INCREASE)
            assertThat(item.dashboard.purchaseSpend.percentageChange).isEqualTo(BigDecimal("50"))
        }
    }

    @Test
    fun `comparison mapping DECREASE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("50"), BigDecimal("100"), BigDecimal("-50"), BigDecimal("-50"))
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.DECREASE)
        }
    }

    @Test
    fun `comparison mapping NO_CHANGE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("100"), BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.NO_CHANGE)
        }
    }

    private fun createEmptySnapshot() = DashboardSnapshot(
        inventory = InventoryValuationSummary(BigDecimal.ZERO, 0, 0, 0),
        purchases = MetricComparison(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null),
        waste = MetricComparison(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null),
        negativeBalanceCount = 0,
        completedCountCount = 0,
        mostRecentCompletedCountAt = null,
        adjustedLineCount = 0,
        activeIngredientsMissingOptionsCount = 0,
        topWasteItems = emptyList(),
        recentActivity = emptyList()
    )
}
