package com.miara.cuentame.feature.counts.export

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountExportRow
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.inventory.StockCountStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StockCountCsvExportTest {

    @Test
    fun `generate should include all columns and preserve snapshots`() {
        val count = StockCount(
            id = StockCountId("c1"),
            restaurantId = RestaurantId("r1"),
            name = "Monthly Count",
            startedAt = Instant.now(),
            effectiveAt = Instant.ofEpochMilli(1723482246000L),
            status = StockCountStatus.COMPLETED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val rows = listOf(
            StockCountExportRow(
                areaName = "Kitchen",
                ingredientName = "Tomato",
                baseUnitSymbol = "kg",
                expectedQuantityBase = "10.0",
                countedQuantityBase = "9.5",
                adjustmentQuantityBase = "-0.5",
                notes = "Loss"
            )
        )

        val csv = StockCountCsvExport.generate(count, rows)
        val lines = csv.lines()

        assertEquals("count_id,completed_at,area,ingredient,base_unit,expected_quantity,counted_quantity,variance_quantity,adjustment_quantity,notes", lines[0])
        val row = lines[1].split(",")
        assertEquals("c1", row[0])
        assertEquals("Kitchen", row[2])
        assertEquals("Tomato", row[3])
        assertEquals("10.0", row[5])
        assertEquals("9.5", row[6])
        assertEquals("-0.5", row[7])
        assertEquals("Loss", row[9])
    }
}
