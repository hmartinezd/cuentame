package com.venkoi.restaurantops.core.domain.service

import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.model.dashboard.DashboardDateRange
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ReportingPeriodCalculatorTest {

    private val timeProvider = mockk<TimeProvider>()
    private lateinit var calculator: ReportingPeriodCalculator
    private val now = Instant.parse("2024-01-31T12:00:00Z")

    @Before
    fun setup() {
        every { timeProvider.now() } returns now
        calculator = ReportingPeriodCalculator(timeProvider)
    }

    @Test
    fun calculatePeriods_7Days() {
        val periods = calculator.calculatePeriods(DashboardDateRange.LAST_7_DAYS)
        
        assertThat(periods.current.endExclusive).isEqualTo(now)
        assertThat(periods.current.startInclusive).isEqualTo(now.minus(7, ChronoUnit.DAYS))
        
        assertThat(periods.previous.endExclusive).isEqualTo(periods.current.startInclusive)
        assertThat(periods.previous.startInclusive).isEqualTo(periods.current.startInclusive.minus(7, ChronoUnit.DAYS))
    }

    @Test
    fun calculatePeriods_30Days() {
        val periods = calculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS)
        
        assertThat(periods.current.endExclusive).isEqualTo(now)
        assertThat(periods.current.startInclusive).isEqualTo(now.minus(30, ChronoUnit.DAYS))
        
        assertThat(periods.previous.endExclusive).isEqualTo(periods.current.startInclusive)
        assertThat(periods.previous.startInclusive).isEqualTo(periods.current.startInclusive.minus(30, ChronoUnit.DAYS))
    }

    @Test
    fun calculatePeriods_90Days() {
        val periods = calculator.calculatePeriods(DashboardDateRange.LAST_90_DAYS)
        
        assertThat(periods.current.endExclusive).isEqualTo(now)
        assertThat(periods.current.startInclusive).isEqualTo(now.minus(90, ChronoUnit.DAYS))
        
        assertThat(periods.previous.endExclusive).isEqualTo(periods.current.startInclusive)
        assertThat(periods.previous.startInclusive).isEqualTo(periods.current.startInclusive.minus(90, ChronoUnit.DAYS))
    }
}
