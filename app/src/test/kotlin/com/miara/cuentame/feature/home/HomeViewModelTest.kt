package com.miara.cuentame.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
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
            val ready = item as HomeScreenState.Ready
            assertThat(ready.restaurantId).isEqualTo(restaurantId)
            assertThat(ready.restaurantName).isEqualTo("Test Rest")
            assertThat(ready.currencyCode).isEqualTo("USD")
            assertThat(ready.localeTag).isEqualTo("en-US")
        }
    }

    @Test
    fun `initial repository failure triggers Error state`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flow {
            throw RuntimeException("Fail")
        }

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            var item = awaitItem()
            while (item is HomeScreenState.Loading) item = awaitItem()
            
            assertThat(item).isInstanceOf(HomeScreenState.Error::class.java)
        }
    }

    @Test
    fun `retry after initial failure resubscribes`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { dashboardRepository.observeDashboard(any(), any()) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
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
    fun `coverage mapping handles edge cases`() = runTest {
        restaurantFlow.value = restaurant
        
        // 1. Zero stocked
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(
            createEmptySnapshot().copy(inventory = InventoryValuationSummary(BigDecimal.ZERO, 0, 0, 0))
        )
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.costCoverage).isNull()
            
            // 2. Populated
            val snapshot = createEmptySnapshot().copy(
                inventory = InventoryValuationSummary(BigDecimal("100"), 8, 10, 2)
            )
            every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
            viewModel.onRetry() // Trigger update
            
            var readyItem = awaitItem()
            while (readyItem !is HomeScreenState.Ready || (readyItem as HomeScreenState.Ready).isRefreshing) {
                readyItem = awaitItem()
            }
            assertThat((readyItem as HomeScreenState.Ready).dashboard.costCoverage).isEqualTo(BigDecimal("80.0"))
        }
    }

    @Test
    fun `mapComparison handles states correctly`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("150"), BigDecimal("100"), BigDecimal("50"), BigDecimal("50")),
            waste = MetricComparison(BigDecimal("50"), BigDecimal("100"), BigDecimal("-50"), BigDecimal("-50"))
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat(item.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.INCREASE)
            assertThat(item.dashboard.wasteValue.comparisonState).isEqualTo(MetricComparisonState.DECREASE)
        }
    }

    @Test
    fun `scale-independent zero handling`() = runTest {
        restaurantFlow.value = restaurant
        // 0.00 should be treated as zero
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("100"), BigDecimal("0.00"), BigDecimal("100"), null)
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
    fun `account switch resets to full Loading and hides old data`() = runTest {
        restaurantFlow.value = restaurant
        val flow1 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        every { dashboardRepository.observeDashboard(RestaurantId("rest-1"), any()) } returns flow1
        
        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            flow1.emit(createEmptySnapshot().copy(negativeBalanceCount = 10))
            var item = awaitItem()
            while (item !is HomeScreenState.Ready) item = awaitItem()
            assertThat((item as HomeScreenState.Ready).restaurantId).isEqualTo(RestaurantId("rest-1"))
            
            // Switch to Rest 2
            val restaurant2 = Restaurant(RestaurantId("rest-2"), "Rest 2", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
            every { dashboardRepository.observeDashboard(RestaurantId("rest-2"), any()) } returns flowOf(createEmptySnapshot())
            restaurantFlow.value = restaurant2
            testDispatcher.scheduler.advanceUntilIdle()
            
            // MUST emit Loading, NOT Ready(isRefreshing=true) because restaurantId changed
            assertThat(awaitItem()).isEqualTo(HomeScreenState.Loading)
            
            val finalItem = awaitItem() as HomeScreenState.Ready
            assertThat(finalItem.restaurantId).isEqualTo(RestaurantId("rest-2"))
            assertThat(finalItem.dashboard.negativeBalanceCount).isEqualTo(0)
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
