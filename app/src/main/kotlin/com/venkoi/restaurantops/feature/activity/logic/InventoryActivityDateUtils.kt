package com.venkoi.restaurantops.feature.activity.logic

import com.venkoi.restaurantops.core.model.inventory.InventoryActivityDateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class InventoryActivityInterval(
    val startInclusive: Instant,
    val endExclusive: Instant
)

object InventoryActivityDateUtils {

    fun datePickerMillisToLocalDate(
        selectedDateMillis: Long
    ): LocalDate =
        Instant.ofEpochMilli(selectedDateMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

    fun localDateToDatePickerMillis(
        date: LocalDate
    ): Long =
        date.atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    fun InventoryActivityDateRange.toInterval(
        today: LocalDate,
        zoneId: ZoneId
    ): InventoryActivityInterval {
        val startDate: LocalDate
        val effectiveEndDateInclusive: LocalDate

        when (this) {
            InventoryActivityDateRange.Last7Days -> {
                startDate = today.minusDays(6)
                effectiveEndDateInclusive = today
            }

            InventoryActivityDateRange.Last30Days -> {
                startDate = today.minusDays(29)
                effectiveEndDateInclusive = today
            }

            InventoryActivityDateRange.Last90Days -> {
                startDate = today.minusDays(89)
                effectiveEndDateInclusive = today
            }

            is InventoryActivityDateRange.Custom -> {
                startDate = this.startDate
                effectiveEndDateInclusive =
                    this.endDateInclusive.coerceAtMost(today)
            }
        }

        require(!startDate.isAfter(effectiveEndDateInclusive)) {
            "Activity start date must not be after its effective end date."
        }

        return InventoryActivityInterval(
            startInclusive =
            startDate.atStartOfDay(zoneId).toInstant(),

            endExclusive =
            effectiveEndDateInclusive
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
        )
    }
}
