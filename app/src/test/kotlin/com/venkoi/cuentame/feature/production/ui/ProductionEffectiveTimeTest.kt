package com.venkoi.cuentame.feature.production.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ProductionEffectiveTimeTest {

    private val zones = listOf(
        ZoneId.of("America/New_York"),
        ZoneId.of("America/Los_Angeles"),
        ZoneOffset.UTC,
        ZoneId.of("Europe/Madrid")
    )

    @Test
    fun `selecting date returns same date locally in all zones`() {
        // August 3, 2026 as UTC midnight millis (what DatePicker returns)
        val selectedDateMillis = LocalDate.of(2026, 8, 3)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        zones.forEach { zoneId ->
            val currentEffectiveAt = Instant.parse("2026-08-01T15:00:00Z")
            val result = calculateEffectiveAtWithNewDate(selectedDateMillis, currentEffectiveAt, zoneId)
            
            val resultLocalDate = result.atZone(zoneId).toLocalDate()
            assertEquals("Failed for zone $zoneId", LocalDate.of(2026, 8, 3), resultLocalDate)
        }
    }

    @Test
    fun `changing date preserves local time and normalizes seconds`() {
        val selectedDateMillis = LocalDate.of(2026, 8, 5)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        zones.forEach { zoneId ->
            val localTime = LocalTime.of(14, 30, 45, 123456)
            val currentEffectiveAt = LocalDateTime.of(LocalDate.of(2026, 8, 1), localTime)
                .atZone(zoneId)
                .toInstant()

            val result = calculateEffectiveAtWithNewDate(selectedDateMillis, currentEffectiveAt, zoneId)
            
            val resultZonedDateTime = result.atZone(zoneId)
            assertEquals("Date mismatch for $zoneId", LocalDate.of(2026, 8, 5), resultZonedDateTime.toLocalDate())
            assertEquals("Hour mismatch for $zoneId", 14, resultZonedDateTime.hour)
            assertEquals("Minute mismatch for $zoneId", 30, resultZonedDateTime.minute)
            assertEquals("Seconds not normalized for $zoneId", 0, resultZonedDateTime.second)
            assertEquals("Nanos not normalized for $zoneId", 0, resultZonedDateTime.nano)
        }
    }

    @Test
    fun `changing time preserves local date and normalizes seconds`() {
        zones.forEach { zoneId ->
            val localDate = LocalDate.of(2026, 8, 10)
            val currentEffectiveAt = LocalDateTime.of(localDate, LocalTime.of(10, 0))
                .atZone(zoneId)
                .toInstant()

            val result = calculateEffectiveAtWithNewTime(18, 45, currentEffectiveAt, zoneId)
            
            val resultZonedDateTime = result.atZone(zoneId)
            assertEquals("Date mismatch for $zoneId", localDate, resultZonedDateTime.toLocalDate())
            assertEquals("Hour mismatch for $zoneId", 18, resultZonedDateTime.hour)
            assertEquals("Minute mismatch for $zoneId", 45, resultZonedDateTime.minute)
            assertEquals("Seconds not normalized for $zoneId", 0, resultZonedDateTime.second)
            assertEquals("Nanos not normalized for $zoneId", 0, resultZonedDateTime.nano)
        }
    }

    @Test
    fun `DST boundary dates produce valid Instant`() {
        // America/New_York DST start 2026: March 8 at 2:00 AM skips to 3:00 AM
        val zoneId = ZoneId.of("America/New_York")
        
        // Try to set date to March 8 while local time is in the gap (e.g. 2:30 AM)
        val selectedDateMillis = LocalDate.of(2026, 3, 8)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
            
        val currentEffectiveAt = LocalDateTime.of(LocalDate.of(2026, 3, 1), LocalTime.of(2, 30))
            .atZone(zoneId)
            .toInstant()

        // This might throw DateTimeException if not handled, but atZone() usually handles it by shifting
        val result = calculateEffectiveAtWithNewDate(selectedDateMillis, currentEffectiveAt, zoneId)
        val resultZonedDateTime = result.atZone(zoneId)
        
        assertEquals(LocalDate.of(2026, 3, 8), resultZonedDateTime.toLocalDate())
        // ZoneId usually shifts to 3:30 AM or something valid
        assertTrue(resultZonedDateTime.toLocalTime() != LocalTime.of(2, 30))
    }
}
