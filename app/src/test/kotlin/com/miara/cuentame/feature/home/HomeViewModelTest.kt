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
    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test Rest", "USD", "en", Instant.now(), Instant.now())

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
    fun `changing range reloads dashboard`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot30 = createEmptySnapshot()
        val snapshot7 = createEmptySnapshot()
        
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flowOf(snapshot30)
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } returns flowOf(snapshot7)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            assertThat(item).isInstanceOf(HomeScreenState.Ready::class.java)
            
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            
            val state = item as HomeScreenState.Ready
            assertThat(state.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
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
