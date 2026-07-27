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
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val detailedReportsRepository = mockk<DetailedReportsRepository>()
    private val periodCalculator = mockk<ReportingPeriodCalculator>()
    
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)
    private val restaurantId = RestaurantId("rest-1")
    private val restaurant = Restaurant(restaurantId, "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val period7 = ReportingPeriod(now.minus(7, ChronoUnit.DAYS), now)
    private val period30 = ReportingPeriod(now.minus(30, ChronoUnit.DAYS), now)
    private val period90 = ReportingPeriod(now.minus(90, ChronoUnit.DAYS), now)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns restaurantFlow
        
        // Default periods stub
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_7_DAYS) } returns ReportingPeriods(period7, period7)
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS) } returns ReportingPeriods(period30, period30)
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_90_DAYS) } returns ReportingPeriods(period90, period90)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `navigation-provided 7-day range`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("range" to DashboardDateRange.LAST_7_DAYS.name))
        val viewModel = PurchaseDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository, periodCalculator)
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_7_DAYS)
    }

    @Test
    fun `navigation-provided 90-day range`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("range" to DashboardDateRange.LAST_90_DAYS.name))
        val viewModel = PurchaseDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository, periodCalculator)
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_90_DAYS)
    }

    @Test
    fun `missing range defaults to 30 days`() = runTest {
        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_30_DAYS)
    }

    @Test
    fun `malformed range defaults to 30 days`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("range" to "INVALID"))
        val viewModel = PurchaseDetailViewModel(savedStateHandle, restaurantRepository, detailedReportsRepository, periodCalculator)
        
        assertThat(viewModel.selectedRange.value).isEqualTo(DashboardDateRange.LAST_30_DAYS)
    }

    @Test
    fun `initial state is Loading then SetupRequired when no restaurant`() = runTest {
        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.SetupRequired)
        }
    }

    @Test
    fun `Ready state success sequence`() = runTest {
        restaurantFlow.value = restaurant
        val report = PurchaseDetailReport(emptyList(), period30, BigDecimal.ZERO, 0)
        every { detailedReportsRepository.observePurchaseDetails(restaurantId, period30) } returns flowOf(report)

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = awaitItem() as DetailReportScreenState.Ready
            assertThat(state.restaurantId).isEqualTo(restaurantId)
            assertThat(state.report.period).isEqualTo(period30)
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun `initial repository failure triggers Error state`() = runTest {
        restaurantFlow.value = restaurant
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } returns flow {
            throw RuntimeException("Fail")
        }

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isInstanceOf(DetailReportScreenState.Error::class.java)
        }
    }

    @Test
    fun `initial retry resubscribes`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(PurchaseDetailReport(emptyList(), period30, BigDecimal.ZERO, 0))
        }

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
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

    @Test
    fun `range change triggers refreshing sequence with distinct periods`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<PurchaseDetailReport>(replay = 1)
        val flow90 = MutableSharedFlow<PurchaseDetailReport>(replay = 1)
        
        every { detailedReportsRepository.observePurchaseDetails(restaurantId, period30) } returns flow30
        every { detailedReportsRepository.observePurchaseDetails(restaurantId, period90) } returns flow90

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Initial load (30 days)
            val report30 = PurchaseDetailReport(emptyList(), period30, BigDecimal("30.00"), 1)
            flow30.emit(report30)
            val initialReady = awaitItem() as DetailReportScreenState.Ready
            assertThat(initialReady.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(initialReady.report.totalSpend).isEqualTo(BigDecimal("30.00"))
            
            // 2. Change range to 90 days
            viewModel.onRangeSelected(DashboardDateRange.LAST_90_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshing = awaitItem() as DetailReportScreenState.Ready
            assertThat(refreshing.isRefreshing).isTrue()
            assertThat(refreshing.selectedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
            assertThat(refreshing.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(refreshing.report.totalSpend).isEqualTo(BigDecimal("30.00")) // Shows old data
            
            // 3. New data arrives
            val report90 = PurchaseDetailReport(emptyList(), period90, BigDecimal("90.00"), 2)
            flow90.emit(report90)
            val finalReady = awaitItem() as DetailReportScreenState.Ready
            assertThat(finalReady.isRefreshing).isFalse()
            assertThat(finalReady.selectedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
            assertThat(finalReady.loadedRange).isEqualTo(DashboardDateRange.LAST_90_DAYS)
            assertThat(finalReady.report.totalSpend).isEqualTo(BigDecimal("90.00"))
        }
    }

    @Test
    fun `selecting same range does not trigger new request`() = runTest {
        restaurantFlow.value = restaurant
        var callCount = 0
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } answers {
            callCount++
            flowOf(PurchaseDetailReport(emptyList(), period30, BigDecimal.ZERO, 0))
        }

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Ready
            
            assertThat(callCount).isEqualTo(1)
            
            viewModel.onRangeSelected(DashboardDateRange.LAST_30_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            expectNoEvents()
            assertThat(callCount).isEqualTo(1)
        }
    }

    @Test
    fun `account switch resets to Loading`() = runTest {
        restaurantFlow.value = restaurant
        every { detailedReportsRepository.observePurchaseDetails(any(), any()) } returns flowOf(
            PurchaseDetailReport(emptyList(), period30, BigDecimal.ZERO, 0)
        )

        val viewModel = PurchaseDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Ready Rest 1
            
            // Switch to Rest 2
            val restaurant2 = Restaurant(RestaurantId("rest-2"), "Rest 2", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
            restaurantFlow.value = restaurant2
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(DetailReportScreenState.Loading)
            val ready2 = awaitItem() as DetailReportScreenState.Ready
            assertThat(ready2.restaurantId).isEqualTo(RestaurantId("rest-2"))
        }
    }
}
