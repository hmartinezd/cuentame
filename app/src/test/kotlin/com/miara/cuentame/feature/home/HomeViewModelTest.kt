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
import io.mockk.verify
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
    private val restaurantId = RestaurantId("rest-1")
    private val restaurant = Restaurant(restaurantId, "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

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
    fun `initial state sequence`() = runTest {
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(createEmptySnapshot())
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(HomeScreenState.Loading)
            
            // 1. Setup Required
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(HomeScreenState.SetupRequired)
            
            // 2. Ready
            restaurantFlow.value = restaurant
            testDispatcher.scheduler.advanceUntilIdle()
            var item = awaitItem()
            while (item is HomeScreenState.Loading) item = awaitItem()
            assertThat((item as HomeScreenState.Ready).restaurantId).isEqualTo(restaurantId)
        }
    }

    @Test
    fun `range refresh sequence`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        val flow7 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Initial load
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 30))
            val initialReady = awaitItem() as HomeScreenState.Ready
            assertThat(initialReady.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 2. Change range
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshing = awaitItem() as HomeScreenState.Ready
            assertThat(refreshing.isRefreshing).isTrue()
            assertThat(refreshing.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(refreshing.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 3. New data arrives
            flow7.emit(createEmptySnapshot().copy(negativeBalanceCount = 7))
            val finalReady = awaitItem() as HomeScreenState.Ready
            assertThat(finalReady.isRefreshing).isFalse()
            assertThat(finalReady.loadedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }

    @Test
    fun `rapid range selection handles cancellation`() = runTest {
        restaurantFlow.value = restaurant
        val flow7 = MutableSharedFlow<DashboardSnapshot>()
        val flow90 = MutableSharedFlow<DashboardSnapshot>()
        
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flowOf(createEmptySnapshot())
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_7_DAYS) } returns flow7
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_90_DAYS) } returns flow90

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Ready 30
            
            // 1. Rapidly switch ranges
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((awaitItem() as HomeScreenState.Ready).isRefreshing).isTrue()

            viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((awaitItem() as HomeScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)

            // 2. Late 7-day emission
            flow7.emit(createEmptySnapshot().copy(negativeBalanceCount = 7))
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 3. Final 90-day emission
            flow90.emit(createEmptySnapshot().copy(negativeBalanceCount = 90))
            val final = awaitItem() as HomeScreenState.Ready
            assertThat(final.dashboard.negativeBalanceCount).isEqualTo(90)
            assertThat(final.loadedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
        }
    }

    @Test
    fun `selecting same range is no-op`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(createEmptySnapshot())
        
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Ready
            
            verify(exactly = 1) { dashboardRepository.observeDashboard(any(), any()) }
            
            viewModel.onRangeSelected(DashboardDateRange.LAST_30_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            expectNoEvents()
            verify(exactly = 1) { dashboardRepository.observeDashboard(any(), any()) }
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
