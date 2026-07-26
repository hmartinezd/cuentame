package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ReportingPeriod(
    val startInclusive: Instant,
    val endExclusive: Instant
)

class ReportingPeriodCalculator @Inject constructor(
    private val timeProvider: TimeProvider
) {
    fun calculateCurrentPeriod(range: DashboardDateRange): ReportingPeriod {
        val now = timeProvider.now()
        val days = when (range) {
            DashboardDateRange.LAST_7_DAYS -> 7L
            DashboardDateRange.LAST_30_DAYS -> 30L
            DashboardDateRange.LAST_90_DAYS -> 90L
        }
        return ReportingPeriod(
            startInclusive = now.minus(days, ChronoUnit.DAYS),
            endExclusive = now
        )
    }

    fun calculatePreviousPeriod(range: DashboardDateRange): ReportingPeriod {
        val current = calculateCurrentPeriod(range)
        val days = when (range) {
            DashboardDateRange.LAST_7_DAYS -> 7L
            DashboardDateRange.LAST_30_DAYS -> 30L
            DashboardDateRange.LAST_90_DAYS -> 90L
        }
        return ReportingPeriod(
            startInclusive = current.startInclusive.minus(days, ChronoUnit.DAYS),
            endExclusive = current.startInclusive
        )
    }
}
