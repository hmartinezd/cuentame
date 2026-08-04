package com.miara.cuentame.feature.activity.logic

import com.miara.cuentame.core.model.inventory.InventoryActivityDateRange
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
        val (startDate, endDateInclusive) = when (this) {
            InventoryActivityDateRange.Last7Days -> today.minusDays(6) to today
            InventoryActivityDateRange.Last30Days -> today.minusDays(29) to today
            InventoryActivityDateRange.Last90Days -> today.minusDays(89) to today
            is InventoryActivityDateRange.Custom -> {
                // Clamp end date to today if it's in the future
                val clampedEnd = if (endDateInclusive.isAfter(today)) today else endDateInclusive
                startDate to clampedEnd
            }
        }
        
        val startInclusive = startDate.atStartOfDay(zoneId).toInstant()
        val endExclusive = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        
        return InventoryActivityInterval(startInclusive, endExclusive)
    }
}
