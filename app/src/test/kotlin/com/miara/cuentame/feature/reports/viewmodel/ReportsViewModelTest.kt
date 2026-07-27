package com.miara.cuentame.feature.reports.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.feature.home.MetricComparisonState
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
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.SetupRequired)
        }
    }

    @Test
    fun `Ready state when restaurant and dashboard success`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot()
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            var item = awaitItem()
            while (item is ReportsScreenState.Loading) item = awaitItem()
            
            val state = item as ReportsScreenState.Ready
            assertThat(state.restaurantName).isEqualTo("Test Rest")
            assertThat(state.currencyCode).isEqualTo("USD")
            assertThat(state.report.inventory.totalValue).isEqualTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `Error state when repository fails`() = runTest {
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
    fun `stale range result cannot overwrite newer range`() = runTest {
        restaurantFlow.value = restaurant
        
        val flow30 = MutableSharedFlow<DashboardSnapshot>()
        val flow7 = MutableSharedFlow<DashboardSnapshot>()
        
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Emit 30-day
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 30))
            var item = awaitItem()
            while (item is ReportsScreenState.Loading) item = awaitItem()
            assertThat((item as ReportsScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 2. Switch to 7 days
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshingItem = awaitItem() as ReportsScreenState.Ready
            assertThat(refreshingItem.isRefreshing).isTrue()
            assertThat(refreshingItem.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(refreshingItem.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 3. Emit 7-day
            flow7.emit(createEmptySnapshot().copy(negativeBalanceCount = 7))
            item = awaitItem()
            assertThat((item as ReportsScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(item.report.alerts.negativeBalanceCount).isEqualTo(7)
            
            // 4. Late 30-day emit
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 999))
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Still on 7-day
            val finalState = viewModel.uiState.value as ReportsScreenState.Ready
            assertThat(finalState.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(finalState.report.alerts.negativeBalanceCount).isEqualTo(7)
        }
    }

    @Test
    fun `coverage mapping handles zero stocked ingredients`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal.ZERO, 0, 0, 0)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            
            assertThat(item.report.inventory.costCoverage).isNull()
        }
    }

    @Test
    fun `retry resubscribes after failure and preserves range`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(createEmptySnapshot())
        }

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
        
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            val errorState = awaitItem() as ReportsScreenState.Error
            assertThat(errorState.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            val readyState = awaitItem() as ReportsScreenState.Ready
            assertThat(readyState.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }

    @Test
    fun `mapComparison handles scale-independent zero`() = runTest {
        restaurantFlow.value = restaurant
        // 0.00 should be treated as zero
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("100"), BigDecimal("0.00"), BigDecimal("100"), null)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat(item.report.purchases.comparisonState).isEqualTo(MetricComparisonState.NEW)
        }
    }

    @Test
    fun `mapComparison handles INCREASE and DECREASE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("150"), BigDecimal("100"), BigDecimal("50"), BigDecimal("50.0")),
            waste = MetricComparison(BigDecimal("50"), BigDecimal("100"), BigDecimal("-50"), BigDecimal("-50.00"))
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat(item.report.purchases.comparisonState).isEqualTo(MetricComparisonState.INCREASE)
            assertThat(item.report.waste.comparisonState).isEqualTo(MetricComparisonState.DECREASE)
        }
    }

    @Test
    fun `mapComparison handles NO_CHANGE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("100.0"), BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat(item.report.purchases.comparisonState).isEqualTo(MetricComparisonState.NO_CHANGE)
        }
    }

    @Test
    fun `default range is LAST_30_DAYS and preserves metadata`() = runTest {
        restaurantFlow.value = restaurant
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(createEmptySnapshot())
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            
            assertThat(item.selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(item.restaurantName).isEqualTo("Test Rest")
            assertThat(item.currencyCode).isEqualTo("USD")
            assertThat(item.localeTag).isEqualTo("en-US")
        }
    }

    @Test
    fun `mapComparison handles zero with different scales`() = runTest {
        restaurantFlow.value = restaurant
        // previous = 0.00, current = 0
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("0"), BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat(item.report.purchases.comparisonState).isEqualTo(MetricComparisonState.NO_CHANGE)
        }
    }

    @Test
    fun `mapComparison handles positive percentageChange as INCREASE`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            purchases = MetricComparison(BigDecimal("125"), BigDecimal("100"), BigDecimal("25"), BigDecimal("25.000"))
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)
        
        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is ReportsScreenState.Ready) item = awaitItem()
            assertThat(item.report.purchases.comparisonState).isEqualTo(MetricComparisonState.INCREASE)
        }
    }

    @Test
    fun `retry preserves LAST_90_DAYS`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_90_DAYS) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(createEmptySnapshot())
        }

        val viewModel = ReportsViewModel(restaurantRepository, dashboardRepository)
        viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
        
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isInstanceOf(ReportsScreenState.Error::class.java)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            val readyState = awaitItem() as ReportsScreenState.Ready
            assertThat(readyState.selectedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
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
