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
            assertThat(awaitItem()).isEqualTo(ReportsScreenState.Loading)
            
            // 3. Emit 7-day
            flow7.emit(createEmptySnapshot().copy(negativeBalanceCount = 7))
            item = awaitItem()
            assertThat((item as ReportsScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(item.report.alerts.negativeBalanceCount).isEqualTo(7)
            
            // 4. Late 30-day emit
            flow30.emit(createEmptySnapshot().copy(negativeBalanceCount = 999))
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Still on 7-day
            assertThat((viewModel.uiState.value as ReportsScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat((viewModel.uiState.value as ReportsScreenState.Ready).report.alerts.negativeBalanceCount).isEqualTo(7)
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
