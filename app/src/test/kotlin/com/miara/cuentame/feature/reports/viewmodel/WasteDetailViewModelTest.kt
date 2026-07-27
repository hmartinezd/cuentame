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
import com.miara.cuentame.core.model.dashboard.WasteDetailReport
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
class WasteDetailViewModelTest {

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
        
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_7_DAYS) } returns ReportingPeriods(period7, period7)
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS) } returns ReportingPeriods(period30, period30)
        every { periodCalculator.calculatePeriods(DashboardDateRange.LAST_90_DAYS) } returns ReportingPeriods(period90, period90)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `range change triggers refreshing sequence with distinct periods`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<WasteDetailReport>(replay = 1)
        val flow7 = MutableSharedFlow<WasteDetailReport>(replay = 1)
        
        every { detailedReportsRepository.observeWasteDetails(restaurantId, period30) } returns flow30
        every { detailedReportsRepository.observeWasteDetails(restaurantId, period7) } returns flow7

        val viewModel = WasteDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // 1. Initial load (30 days)
            flow30.emit(WasteDetailReport(emptyList(), period30, BigDecimal("30.00"), 1))
            val initialReady = awaitItem() as DetailReportScreenState.Ready
            assertThat(initialReady.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            assertThat(initialReady.report.totalWasteValue).isEqualTo(BigDecimal("30.00"))
            
            // 2. Change range to 7 days
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val refreshing = awaitItem() as DetailReportScreenState.Ready
            assertThat(refreshing.isRefreshing).isTrue()
            assertThat(refreshing.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(refreshing.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
            
            // 3. New data arrives
            flow7.emit(WasteDetailReport(emptyList(), period7, BigDecimal("7.00"), 1))
            val finalReady = awaitItem() as DetailReportScreenState.Ready
            assertThat(finalReady.isRefreshing).isFalse()
            assertThat(finalReady.selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(finalReady.loadedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
            assertThat(finalReady.report.totalWasteValue).isEqualTo(BigDecimal("7.00"))
        }
    }

    @Test
    fun `refresh failure preserves old data`() = runTest {
        restaurantFlow.value = restaurant
        val flow30 = MutableSharedFlow<WasteDetailReport>(replay = 1)
        
        every { detailedReportsRepository.observeWasteDetails(restaurantId, period30) } returns flow30
        every { detailedReportsRepository.observeWasteDetails(restaurantId, period7) } returns flow { throw RuntimeException("Fail") }

        val viewModel = WasteDetailViewModel(SavedStateHandle(), restaurantRepository, detailedReportsRepository, periodCalculator)
        viewModel.uiState.test {
            awaitItem() // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            flow30.emit(WasteDetailReport(emptyList(), period30, BigDecimal("30.00"), 1))
            awaitItem() // Ready
            
            viewModel.onRangeSelected(DashboardDateRange.LAST_7_DAYS)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat((awaitItem() as DetailReportScreenState.Ready).isRefreshing).isTrue()
            
            val errorItem = awaitItem() as DetailReportScreenState.Ready
            assertThat(errorItem.refreshError).isTrue()
            assertThat(errorItem.report.totalWasteValue).isEqualTo(BigDecimal("30.00"))
            assertThat(errorItem.loadedRange).isEqualTo(DashboardDateRange.LAST_30_DAYS)
        }
    }
}
