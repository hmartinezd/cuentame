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

data class ReportingPeriods(
    val current: ReportingPeriod,
    val previous: ReportingPeriod
)

class ReportingPeriodCalculator @Inject constructor(
    private val timeProvider: TimeProvider
) {
    fun calculatePeriods(range: DashboardDateRange): ReportingPeriods {
        val now = timeProvider.now()
        val days = when (range) {
            DashboardDateRange.LAST_7_DAYS -> 7L
            DashboardDateRange.LAST_30_DAYS -> 30L
            DashboardDateRange.LAST_90_DAYS -> 90L
        }
        
        val currentStart = now.minus(days, ChronoUnit.DAYS)

        return ReportingPeriods(
            current = ReportingPeriod(
                startInclusive = currentStart,
                endExclusive = now
            ),
            previous = ReportingPeriod(
                startInclusive = currentStart.minus(days, ChronoUnit.DAYS),
                endExclusive = currentStart
            )
        )
    }
}
