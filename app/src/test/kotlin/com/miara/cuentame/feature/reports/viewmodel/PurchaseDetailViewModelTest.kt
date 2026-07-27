package com.miara.cuentame.feature.reports.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
import com.miara.cuentame.core.domain.service.ReportingPeriods
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.PurchaseDetailReport
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
class PurchaseDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val detailedReportsRepository = mockk<DetailedReportsRepository>()
    private val periodCalculator = mockk<ReportingPeriodCalculator>()
    
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)
    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
    private val period = ReportingPeriod(Instant.EPOCH, Instant.now())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns restaurantFlow
        every { periodCalculator.calculatePeriods(any()) } returns ReportingPeriods(period, period)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial range from SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("range" to DashboardDateRange.LAST_7_DAYS.name))
        val viewModel = PurchaseDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository, periodCalculator)
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_7_DAYS)
    }

    @Test
    fun `malformed range defaults to LAST_30_DAYS`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("range" to "INVALID"))
        val viewModel = PurchaseDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository, periodCalculator)
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_30_DAYS)
    }

    @Test
    fun `Ready state when repository success`() = runTest {
        restaurantFlow.value = restaurant
        val report = PurchaseDetailReport(emptyList(), period, BigDecimal.ZERO, 0)
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } returns flowOf(report)

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = awaitItem() as DetailReportScreenState.Ready
            assertThat(state.report.totalSpend).isEqualTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `range change triggers new report`() = runTest {
        restaurantFlow.value = restaurant
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } returns flowOf(
            PurchaseDetailReport(emptyList(), period, BigDecimal.ZERO, 0)
        )

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Ready
            
            viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Ready::class.java)
        }
    }

    @Test
    fun `retry preserves selected range`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(PurchaseDetailReport(emptyList(), period, BigDecimal.ZERO, 0))
        }

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
        
        viewModel.uiState.test {
            // Skip initial emissions until we reach the error from the 7-day range selection
            var item = awaitItem()
            while (item !is DetailReportScreenState.Error) {
                item = awaitItem()
            }
            
            assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            
            viewModel.onRetry()
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Ready::class.java)
            assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_7_DAYS)
        }
    }
}
