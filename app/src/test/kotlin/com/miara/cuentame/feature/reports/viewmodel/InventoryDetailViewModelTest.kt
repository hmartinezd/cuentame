package com.miara.cuentame.feature.reports.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport
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
class InventoryDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val detailedReportsRepository = mockk<DetailedReportsRepository>()
    
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
        val viewModel = InventoryDetailViewModel(restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.SetupRequired)
        }
    }

    @Test
    fun `Ready state when restaurant and report success`() = runTest {
        restaurantFlow.value = restaurant
        val report = InventoryDetailReport(emptyList(), BigDecimal.ZERO, 0, 0, 0, 0, 0)
        every { detailedReportsRepository.observeInventoryDetails(any()) } returns flowOf(report)

        val viewModel = InventoryDetailViewModel(restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = awaitItem() as DetailReportScreenState.Ready
            assertThat(state.restaurantName).isEqualTo("Test Rest")
            assertThat(state.report.totalValue).isEqualTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `Error state when repository fails`() = runTest {
        restaurantFlow.value = restaurant
        every { detailedReportsRepository.observeInventoryDetails(any()) } returns flow {
            throw RuntimeException("Fail")
        }

        val viewModel = InventoryDetailViewModel(restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Error::class.java)
        }
    }

    @Test
    fun `retry resubscribes`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { detailedReportsRepository.observeInventoryDetails(any()) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(InventoryDetailReport(emptyList(), BigDecimal.ZERO, 0, 0, 0, 0, 0))
        }

        val viewModel = InventoryDetailViewModel(restaurantRepository, detailedReportsRepository)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Error::class.java)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Ready::class.java)
        }
    }
}
