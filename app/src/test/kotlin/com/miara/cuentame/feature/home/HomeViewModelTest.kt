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
    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test Rest", "USD", "en-US", Instant.now(), Instant.now())

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
            assertThat(state.selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
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
            assertThat(state.selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
        }
    }

    @Test
    fun `changing range to 7 days reloads dashboard`() = runTest {
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
            assertThat((item as HomeScreenState.Ready).selectedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)

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

    @Test
    fun `changing range to 90 days reloads dashboard`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot30 = createEmptySnapshot()
        val snapshot90 = createEmptySnapshot()

        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flowOf(snapshot30)
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_90_DAYS) } returns flowOf(snapshot90)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()

            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.selectedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
        }
    }

    @Test
    fun `retry resubscribes after failure`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { dashboardRepository.observeDashboard(any(), any()) } returns flow {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("First call fails")
            } else {
                emit(createEmptySnapshot())
            }
        }

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            assertThat(item).isInstanceOf(HomeScreenState.Error::class.java)

            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()

            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }
            assertThat(item).isInstanceOf(HomeScreenState.Ready::class.java)
        }
    }

    @Test
    fun `stale range result is ignored when newer range is selected`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<DashboardSnapshot>()
        val flow7 = MutableSharedFlow<DashboardSnapshot>()

        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_30_DAYS) } returns flow30
        every { dashboardRepository.observeDashboard(any(), DashboardDateRange.LAST_7_DAYS) } returns flow7

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            // Start 30-day collection
            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            // Switch to 7 days
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()

            item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            // Emit 7-day snapshot
            val snapshot7 = createEmptySnapshot().copy(
                inventory = InventoryValuationSummary(BigDecimal("700"), 1, 1, 0)
            )
            flow7.emit(snapshot7)
            testDispatcher.scheduler.advanceUntilIdle()

            item = awaitItem()
            assertThat((item as HomeScreenState.Ready).dashboard.inventoryValue).isEqualTo(BigDecimal("700"))

            // Late 30-day snapshot should be ignored
            val snapshot30 = createEmptySnapshot().copy(
                inventory = InventoryValuationSummary(BigDecimal("3000"), 1, 1, 0)
            )
            flow30.emit(snapshot30)
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify state remains 7-day
            assertThat(viewModel.uiState.value).isInstanceOf(HomeScreenState.Ready::class.java)
            val readyState = viewModel.uiState.value as HomeScreenState.Ready
            assertThat(readyState.dashboard.inventoryValue).isEqualTo(BigDecimal("700"))
            assertThat(readyState.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }

    @Test
    fun `coverage calculation handles zero stocked count`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal.ZERO, 0, 0, 0)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.stockedIngredientCount).isEqualTo(0)
            assertThat(state.dashboard.valuedIngredientCount).isEqualTo(0)
            assertThat(state.dashboard.costCoverage).isNull()
        }
    }

    @Test
    fun `coverage calculation uses BigDecimal for precision`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal.ZERO, 8, 10, 0)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.stockedIngredientCount).isEqualTo(10)
            assertThat(state.dashboard.valuedIngredientCount).isEqualTo(8)
            // 8/10 * 100 = 80
            assertThat(state.dashboard.costCoverage).isEqualTo(BigDecimal("80"))
        }
    }

    @Test
    fun `metric comparison NEW state when previous zero and current positive`() = runTest {
        restaurantFlow.value = restaurant
        val comparison = MetricComparison(
            current = BigDecimal("100"),
            previous = BigDecimal.ZERO,
            absoluteChange = BigDecimal("100"),
            percentageChange = null
        )
        val snapshot = createEmptySnapshot().copy(purchases = comparison)
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.NEW)
        }
    }

    @Test
    fun `metric comparison INCREASE state`() = runTest {
        restaurantFlow.value = restaurant
        val comparison = MetricComparison(
            current = BigDecimal("150"),
            previous = BigDecimal("100"),
            absoluteChange = BigDecimal("50"),
            percentageChange = BigDecimal("50")
        )
        val snapshot = createEmptySnapshot().copy(purchases = comparison)
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.INCREASE)
            assertThat(state.dashboard.purchaseSpend.percentageChange).isEqualTo(BigDecimal("50"))
        }
    }

    @Test
    fun `metric comparison DECREASE state`() = runTest {
        restaurantFlow.value = restaurant
        val comparison = MetricComparison(
            current = BigDecimal("50"),
            previous = BigDecimal("100"),
            absoluteChange = BigDecimal("-50"),
            percentageChange = BigDecimal("-50")
        )
        val snapshot = createEmptySnapshot().copy(purchases = comparison)
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.DECREASE)
            assertThat(state.dashboard.purchaseSpend.percentageChange).isEqualTo(BigDecimal("-50"))
        }
    }

    @Test
    fun `metric comparison NO_CHANGE state`() = runTest {
        restaurantFlow.value = restaurant
        val comparison = MetricComparison(
            current = BigDecimal("100"),
            previous = BigDecimal("100"),
            absoluteChange = BigDecimal.ZERO,
            percentageChange = BigDecimal.ZERO
        )
        val snapshot = createEmptySnapshot().copy(purchases = comparison)
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.purchaseSpend.comparisonState).isEqualTo(MetricComparisonState.NO_CHANGE)
        }
    }

    @Test
    fun `valued and stocked ingredient counts are preserved`() = runTest {
        restaurantFlow.value = restaurant
        val snapshot = createEmptySnapshot().copy(
            inventory = InventoryValuationSummary(BigDecimal("1000"), 15, 20, 5)
        )
        every { dashboardRepository.observeDashboard(any(), any()) } returns flowOf(snapshot)

        val viewModel = HomeViewModel(restaurantRepository, dashboardRepository)
        viewModel.uiState.test {
            awaitItem() // Initial Loading
            testDispatcher.scheduler.advanceUntilIdle()

            var item = awaitItem()
            while (item is HomeScreenState.Loading) {
                item = awaitItem()
            }

            val state = item as HomeScreenState.Ready
            assertThat(state.dashboard.valuedIngredientCount).isEqualTo(15)
            assertThat(state.dashboard.stockedIngredientCount).isEqualTo(20)
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


