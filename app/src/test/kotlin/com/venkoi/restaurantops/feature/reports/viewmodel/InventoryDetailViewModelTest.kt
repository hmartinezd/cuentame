package com.venkoi.restaurantops.feature.reports.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.DetailedReportsRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.dashboard.InventoryDetailReport
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
class InventoryDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val detailedReportsRepository = mockk<DetailedReportsRepository>()
    private val savedStateHandle = SavedStateHandle()
    
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
        val viewModel = InventoryDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.SetupRequired)
        }
    }

    @Test
    fun `Ready state refresh sequence`() = runTest {
        restaurantFlow.value = restaurant
        val flow1 = MutableSharedFlow<InventoryDetailReport>()
        every { detailedReportsRepository.observeInventoryDetails(restaurantId) } returns flow1

        val viewModel = InventoryDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Initial load
            flow1.emit(InventoryDetailReport(emptyList(), BigDecimal.ZERO, 0, 0, 0, 0, 0))
            var item = awaitItem()
            while (item !is DetailReportScreenState.Ready) item = awaitItem()
            assertThat((item as DetailReportScreenState.Ready).isRefreshing).isFalse()
            
            // 2. Refresh (via retry while ready)
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshing = awaitItem() as DetailReportScreenState.Ready
            assertThat(refreshing.isRefreshing).isTrue()
            
            // 3. New data
            flow1.emit(InventoryDetailReport(emptyList(), BigDecimal.TEN, 1, 1, 1, 0, 0))
            val finalReady = awaitItem() as DetailReportScreenState.Ready
            assertThat(finalReady.isRefreshing).isFalse()
            assertThat(finalReady.report.totalValue).isEqualTo(BigDecimal.TEN)
        }
    }

    @Test
    fun `account switch resets to full Loading and hides old data`() = runTest {
        restaurantFlow.value = restaurant
        val flow1 = MutableSharedFlow<InventoryDetailReport>(replay = 1)
        every { detailedReportsRepository.observeInventoryDetails(RestaurantId("rest-1")) } returns flow1
        
        val viewModel = InventoryDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            flow1.emit(InventoryDetailReport(emptyList(), BigDecimal.TEN, 1, 1, 1, 0, 0))
            var item = awaitItem()
            while (item !is DetailReportScreenState.Ready) item = awaitItem()
            assertThat((item as DetailReportScreenState.Ready).restaurantId).isEqualTo(RestaurantId("rest-1"))
            
            // Switch to Rest 2
            val restaurant2 = Restaurant(RestaurantId("rest-2"), "Rest 2", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
            every { detailedReportsRepository.observeInventoryDetails(RestaurantId("rest-2")) } returns flowOf(
                InventoryDetailReport(emptyList(), BigDecimal.ZERO, 0, 0, 0, 0, 0)
            )
            restaurantFlow.value = restaurant2
            testDispatcher.scheduler.advanceUntilIdle()
            
            // MUST emit Loading, NOT Ready(isRefreshing=true) because restaurantId changed
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            
            val finalItem = awaitItem() as DetailReportScreenState.Ready
            assertThat(finalItem.restaurantId).isEqualTo(RestaurantId("rest-2"))
            assertThat(finalItem.report.totalValue).isEqualTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `refresh failure preserves old data`() = runTest {
        restaurantFlow.value = restaurant
        val flow1 = MutableSharedFlow<InventoryDetailReport>(replay = 1)
        
        // Initial success
        every { detailedReportsRepository.observeInventoryDetails(restaurantId) } returns flow1

        val viewModel = InventoryDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            flow1.emit(InventoryDetailReport(emptyList(), BigDecimal("123"), 1, 1, 1, 0, 0))
            awaitItem() // Ready
            
            // Fail on next subscription
            every { detailedReportsRepository.observeInventoryDetails(restaurantId) } returns flow { throw RuntimeException("Fail") }
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat((awaitItem() as DetailReportScreenState.Ready).isRefreshing).isTrue()
            
            val errorItem = awaitItem() as DetailReportScreenState.Ready
            assertThat(errorItem.refreshError).isTrue()
            assertThat(errorItem.report.totalValue).isEqualTo(BigDecimal("123"))
        }
    }
}
