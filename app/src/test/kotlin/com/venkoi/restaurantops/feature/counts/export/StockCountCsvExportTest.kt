package com.venkoi.restaurantops.feature.counts.export

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.domain.repository.StockCountExportRow
import com.venkoi.restaurantops.core.model.count.StockCount
import com.venkoi.restaurantops.core.model.inventory.StockCountStatus
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

    @Test(expected = com.venkoi.restaurantops.core.domain.validation.ValidationError.MalformedStockCountMovementHistory::class)
    fun `TEST A - generate should throw MalformedStockCountMovementHistory when counted is malformed`() {
        val count = createTestCount()
        val rows = listOf(
            StockCountExportRow("A", "I", "u", "10", "not-a-number", null, null)
        )
        StockCountCsvExport.generate(count, rows)
    }

    @Test(expected = com.venkoi.restaurantops.core.domain.validation.ValidationError.MalformedStockCountMovementHistory::class)
    fun `TEST B - generate should throw MalformedStockCountMovementHistory when expected is malformed`() {
        val count = createTestCount()
        val rows = listOf(
            StockCountExportRow("A", "I", "u", "broken", "5", null, null)
        )
        StockCountCsvExport.generate(count, rows)
    }

    @Test(expected = com.venkoi.restaurantops.core.domain.validation.ValidationError.MalformedStockCountMovementHistory::class)
    fun `TEST C - generate should throw MalformedStockCountMovementHistory when adjustment is malformed`() {
        val count = createTestCount()
        val rows = listOf(
            StockCountExportRow("A", "I", "u", "10", "5", "broken", null)
        )
        StockCountCsvExport.generate(count, rows)
    }

    @Test
    fun `TEST D - generate should handle legitimate null expected and adjustment`() {
        val count = createTestCount()
        val rows = listOf(
            StockCountExportRow("A", "I", "u", null, "5", "5", null)
        )
        val csv = StockCountCsvExport.generate(count, rows)
        val row = csv.lines()[1].split(",")
        assertEquals("", row[8])  // expected
        assertEquals("5", row[9]) // counted
        assertEquals("", row[10]) // variance
        assertEquals("5", row[11]) // adjustment
    }

    @Test
    fun `TEST E - generate should handle real zero`() {
        val count = createTestCount()
        val rows = listOf(
            StockCountExportRow("A", "I", "u", "5", "0", "-5", null)
        )
        val csv = StockCountCsvExport.generate(count, rows)
        val row = csv.lines()[1].split(",")
        assertEquals("5", row[8])  // expected
        assertEquals("0", row[9])  // counted
        assertEquals("-5", row[10]) // variance
        assertEquals("-5", row[11]) // adjustment
    }

    private fun createTestCount() = StockCount(
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
}
