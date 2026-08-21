package com.venkoi.restaurantops.feature.reports.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.DashboardRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.dashboard.*
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
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
class ReportsViewModelTest {

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
    fun `initial state is Loading then SetupRequired when no restaurant`() = runTest {
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.SetupRequired)
        }
    }

    @Test
    fun `initial repository failure triggers Error state`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flow {
            throw RuntimeException("Fail")
        }

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            var item = awaitItem()
            while (item is ReportsScreenState.Loading) item = awaitItem()
            
            assertThat(item).isInstanceOf(ReportsScreenState.Error::class.java)
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

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isInstanceOf(ReportsScreenState.Error::class.java)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            assertThat(awaitItem()).isInstanceOf(ReportsScreenState.Ready::class.java)
        }
    }

    @Test
    fun `Ready state refresh sequence`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        val flow7 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(restaurantId, DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Initial load
            flow30.emit(createEmptySnapshot())
            var item = awaitItem()
            while (item is ReportsScreenState.Loading) item = awaitItem()
            val initialReady = item as ReportsScreenState.Ready
            assertThat(initialReady.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 2. Refresh
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshing = awaitItem() as ReportsScreenState.Ready
            assertThat(refreshing.isRefreshing).isTrue()
            
            // 3. Complete
            flow7.emit(createEmptySnapshot())
            val finalReady = awaitItem() as ReportsScreenState.Ready
            assertThat(finalReady.isRefreshing).isFalse()
            assertThat(finalReady.loadedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }

    @Test
    fun `account switch resets to full Loading and hides old data`() = runTest {
        restaurantFlow.value = restaurant
        val flow1 = MutableSharedFlow<DashboardSnapshot>(replay = 1)
        every { dashboardRepository.observeDashboard(RestaurantId("rest-1"), any()) } returns flow1
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            flow1.emit(createEmptySnapshot())
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat((item as ReportsScreenState.Ready).restaurantId).isEqualTo(RestaurantId("rest-1"))
            
            // Switch to Rest 2
            val restaurant2 = Restaurant(RestaurantId("rest-2"), "Rest 2", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
            every { dashboardRepository.observeDashboard(RestaurantId("rest-2"), any()) } returns flowOf(createEmptySnapshot())
            restaurantFlow.value = restaurant2
            testDispatcher.scheduler.advanceUntilIdle()
            
            // MUST emit Loading, NOT Ready(isRefreshing=true) because restaurantId changed
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            
            val finalItem = awaitItem() as ReportsScreenState.Ready
            assertThat(finalItem.restaurantId).isEqualTo(RestaurantId("rest-2"))
        }
    }

    @Test
    fun `mapping coverage and fields`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal("100"), 8, 10, 2),
            negativeBalanceCount = 5,
            activeIngredientsMissingOptionsCount = 3
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            val report = item.report
            assertThat(report.inventory.totalValue).isEqualTo(BigDecimal("100"))
            assertThat(report.inventory.costCoverage).isEqualTo(BigDecimal("80.0"))
            assertThat(report.alerts.negativeBalanceCount).isEqualTo(5)
            assertThat(report.alerts.missingOptionsCount).isEqualTo(3)
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
