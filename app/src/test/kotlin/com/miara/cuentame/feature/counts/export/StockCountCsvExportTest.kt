package com.miara.cuentame.feature.counts.export

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountExportRow
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.inventory.StockCountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StockCountCsvExportTest {

    private val t1 = Instant.parse("2024-01-01T12:00:00Z")
    private val t2 = Instant.parse("2024-01-01T13:00:00Z")
    private val t3 = Instant.parse("2024-01-01T14:00:00Z")

    @Test
    fun `generate should handle separate timestamps and status`() {
        val count = StockCount(
            id = StockCountId("c1"),
            restaurantId = RestaurantId("r1"),
            name = "Test Count",
            startedAt = t1,
            effectiveAt = t1,
            completedAt = t2,
            voidedAt = t3,
            status = StockCountStatus.VOIDED,
            createdAt = t1,
            updatedAt = t2
        )
        val rows = listOf(
            StockCountExportRow("Area 1", "Ing 1", "kg", "10", "9", "-1", "Notes")
        )

        val csv = StockCountCsvExport.generate(count, rows)
        val header = csv.lines()[0]
        val row = csv.lines()[1].split(",")

        assertEquals("count_id,status,effective_at,completed_at,voided_at,area,ingredient,base_unit,expected_quantity,counted_quantity,variance_quantity,adjustment_quantity,notes", header)
        assertEquals("c1", row[0])
        assertEquals("VOIDED", row[1])
        assertTrue(row[2].contains("2024-01-01T12:00:00Z"))
        assertTrue(row[3].contains("2024-01-01T13:00:00Z"))
        assertTrue(row[4].contains("2024-01-01T14:00:00Z"))
    }

    @Test
    fun `variance should be calculated from snapshots and handles unknown expected`() {
        val count = StockCount(
            id = StockCountId("c1"),
            restaurantId = RestaurantId("r1"),
            name = "Test Count",
            startedAt = t1,
            effectiveAt = t1,
            completedAt = t2,
            status = StockCountStatus.COMPLETED,
            createdAt = t1,
            updatedAt = t2
        )
        val rows = listOf(
            // TEST B: Known expected
            StockCountExportRow("A", "Tomato", "kg", "10.0", "9.5", "-0.5", null),
            // TEST C: Unknown expected / Initial count
            StockCountExportRow("A", "Onion", "kg", null, "5.0", "5.0", null),
            // TEST D: Variance vs Adjustment separation
            StockCountExportRow("A", "Potato", "kg", "10", "9", "-0.75", null)
        )

        val csv = StockCountCsvExport.generate(count, rows)
        val lines = csv.lines()

        // Row 1: 9.5 - 10.0 = -0.5
        val r1 = lines[1].split(",")
        assertEquals("10", r1[8])
        assertEquals("9.5", r1[9])
        assertEquals("-0.5", r1[10])
        assertEquals("-0.5", r1[11])

        // Row 2: expected null -> variance null, adjustment 5.0
        val r2 = lines[2].split(",")
        assertEquals("", r2[8])
        assertEquals("5", r2[9])
        assertEquals("", r2[10])
        assertEquals("5", r2[11])

        // Row 3: 9 - 10 = -1 variance, -0.75 adjustment
        val r3 = lines[3].split(",")
        assertEquals("10", r3[8])
        assertEquals("9", r3[9])
        assertEquals("-1", r3[10])
        assertEquals("-0.75", r3[11])
    }

    @Test
    fun `generate should escape area and ingredient names`() {
        val count = StockCount(
            id = StockCountId("c1"),
            restaurantId = RestaurantId("r1"),
            name = "Test Count",
            startedAt = t1,
            effectiveAt = t1,
            completedAt = t2,
            status = StockCountStatus.COMPLETED,
            createdAt = t1,
            updatedAt = t2
        )
        val rows = listOf(
            StockCountExportRow("Kitchen, Main", "Tomato \"Roma\"", "kg", "1", "1", "0", null)
        )

        val csv = StockCountCsvExport.generate(count, rows)
        val rowLine = csv.lines()[1]
        
        assertTrue(rowLine.contains("\"Kitchen, Main\""))
        assertTrue(rowLine.contains("\"Tomato \"\"Roma\"\"\""))
    }
}
