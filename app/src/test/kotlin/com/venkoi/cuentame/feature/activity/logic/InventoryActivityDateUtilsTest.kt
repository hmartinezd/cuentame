package com.venkoi.cuentame.feature.activity.logic

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.model.inventory.InventoryActivityDateRange
import com.venkoi.cuentame.feature.activity.logic.InventoryActivityDateUtils.toInterval
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class InventoryActivityDateUtilsTest {

    private val today = LocalDate.of(2026, 8, 4)
    private val zoneNy = ZoneId.of("America/New_York")
    private val zoneLa = ZoneId.of("America/Los_Angeles")
    private val zoneMadrid = ZoneId.of("Europe/Madrid")
    private val zoneUtc = ZoneOffset.UTC

    @Test
    fun `datePickerMillisToLocalDate converts UTC millis correctly`() {
        // August 15, 2026 UTC
        val millis = LocalDate.of(2026, 8, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        
        assertThat(InventoryActivityDateUtils.datePickerMillisToLocalDate(millis))
            .isEqualTo(LocalDate.of(2026, 8, 15))
    }

    @Test
    fun `localDateToDatePickerMillis converts to UTC millis correctly`() {
        val date = LocalDate.of(2026, 8, 15)
        val expectedMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        
        assertThat(InventoryActivityDateUtils.localDateToDatePickerMillis(date))
            .isEqualTo(expectedMillis)
    }

    @Test
    fun `Last 7 Days includes today and previous 6 dates`() {
        val interval = InventoryActivityDateRange.Last7Days.toInterval(today, zoneUtc)
        
        // Start: 2026-08-04 - 6 days = 2026-07-29
        assertThat(interval.startInclusive).isEqualTo(LocalDate.of(2026, 7, 29).atStartOfDay(zoneUtc).toInstant())
        // End: 2026-08-04 + 1 day = 2026-08-05
        assertThat(interval.endExclusive).isEqualTo(LocalDate.of(2026, 8, 5).atStartOfDay(zoneUtc).toInstant())
    }

    @Test
    fun `Last 30 Days includes today and previous 29 dates`() {
        val interval = InventoryActivityDateRange.Last30Days.toInterval(today, zoneUtc)
        
        assertThat(interval.startInclusive).isEqualTo(LocalDate.of(2026, 7, 6).atStartOfDay(zoneUtc).toInstant())
        assertThat(interval.endExclusive).isEqualTo(LocalDate.of(2026, 8, 5).atStartOfDay(zoneUtc).toInstant())
    }

    @Test
    fun `Custom range boundaries are correct`() {
        val start = LocalDate.of(2026, 8, 1)
        val end = LocalDate.of(2026, 8, 3)
        val interval = InventoryActivityDateRange.Custom(start, end).toInterval(today, zoneUtc)
        
        assertThat(interval.startInclusive).isEqualTo(start.atStartOfDay(zoneUtc).toInstant())
        assertThat(interval.endExclusive).isEqualTo(end.plusDays(1).atStartOfDay(zoneUtc).toInstant())
    }

    @Test
    fun `datePickerMillisToLocalDate conversion boundary across timezones`() {
        // Selection: August 15, 2026
        val targetDate = LocalDate.of(2026, 8, 15)
        val millis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val zones = listOf(
            ZoneOffset.UTC,
            ZoneId.of("America/New_York"),
            ZoneId.of("America/Los_Angeles"),
            ZoneId.of("Europe/Madrid"),
            ZoneId.of("Asia/Tokyo")
        )

        zones.forEach { zone ->
            val converted = InventoryActivityDateUtils.datePickerMillisToLocalDate(millis)
            assertThat(converted).isEqualTo(targetDate)
            
            val backToMillis = InventoryActivityDateUtils.localDateToDatePickerMillis(converted)
            assertThat(backToMillis).isEqualTo(millis)
        }
    }

    @Test
    fun `Custom range clamps future end date to today exactly`() {
        // today = August 4, 2026
        // selected end = August 10, 2026
        // effective end = August 4, 2026
        // endExclusive = local start of August 5, 2026
        val start = LocalDate.of(2026, 8, 1)
        val end = LocalDate.of(2026, 8, 10)
        val interval = InventoryActivityDateRange.Custom(start, end).toInterval(today, zoneUtc)
        
        val expectedEndExclusive = LocalDate.of(2026, 8, 5).atStartOfDay(zoneUtc).toInstant()
        assertThat(interval.endExclusive).isEqualTo(expectedEndExclusive)
    }

    @Test
    fun `Interval start and end respect ZoneId`() {
        val range = InventoryActivityDateRange.Last7Days
        
        val intervalNy = range.toInterval(today, zoneNy)
        val intervalLa = range.toInterval(today, zoneLa)
        
        assertThat(intervalNy.startInclusive).isEqualTo(LocalDate.of(2026, 7, 29).atStartOfDay(zoneNy).toInstant())
        assertThat(intervalLa.startInclusive).isEqualTo(LocalDate.of(2026, 7, 29).atStartOfDay(zoneLa).toInstant())
        
        // At 2026-07-29T00:00:00, NY is ahead of LA
        assertThat(intervalNy.startInclusive).isNotEqualTo(intervalLa.startInclusive)
    }

    @Test
    fun `DST start handling (Spring forward)`() {
        // America/New_York DST starts on March 8, 2026 (02:00 -> 03:00)
        val springDay = LocalDate.of(2026, 3, 8)
        val range = InventoryActivityDateRange.Custom(springDay, springDay)
        
        val interval = range.toInterval(today, zoneNy)
        
        assertThat(interval.startInclusive).isEqualTo(springDay.atStartOfDay(zoneNy).toInstant())
        assertThat(interval.endExclusive).isEqualTo(springDay.plusDays(1).atStartOfDay(zoneNy).toInstant())
    }

    @Test
    fun `DST end handling (Fall back)`() {
        // America/New_York DST ends on November 1, 2026 (02:00 -> 01:00)
        val fallDay = LocalDate.of(2026, 11, 1)
        val range = InventoryActivityDateRange.Custom(fallDay, fallDay)
        
        val interval = range.toInterval(LocalDate.of(2026, 11, 2), zoneNy)
        
        assertThat(interval.startInclusive).isEqualTo(fallDay.atStartOfDay(zoneNy).toInstant())
        assertThat(interval.endExclusive).isEqualTo(fallDay.plusDays(1).atStartOfDay(zoneNy).toInstant())
    }
}
