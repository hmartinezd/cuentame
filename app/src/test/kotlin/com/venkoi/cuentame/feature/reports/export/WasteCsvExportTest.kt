package com.venkoi.cuentame.feature.reports.export

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.WasteEventId
import com.venkoi.cuentame.core.model.dashboard.WasteDetailItem
import com.venkoi.cuentame.core.model.dashboard.WasteDetailReport
import com.venkoi.cuentame.core.model.inventory.WasteReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class WasteCsvExportTest {

    @Test
    fun `generate should include all columns`() {
        val rows = listOf(
            WasteDetailItem(
                wasteEventId = WasteEventId("w1"),
                ingredientId = IngredientId("i1"),
                ingredientName = "Tomato",
                areaName = "Kitchen",
                reason = WasteReason.SPOILED,
                timestamp = Instant.ofEpochMilli(1723482246000L),
                quantityBase = BigDecimal("2.0"),
                baseUnitSymbol = "kg",
                historicalValue = BigDecimal("5.0"),
                notes = "Some notes"
            )
        )
        val report = WasteDetailReport(
            rows = rows,
            period = com.venkoi.cuentame.core.domain.service.ReportingPeriod(Instant.MIN, Instant.MAX),
            totalWasteValue = BigDecimal("5.0"),
            recordCount = 1
        )

        val csv = WasteCsvExport.generate(report)
        val lines = csv.lines()

        assertEquals("timestamp,ingredient,area,quantity,base_unit,reason,waste_value,notes", lines[0])
        assertEquals("Tomato", lines[1].split(",")[1])
        assertEquals("Kitchen", lines[1].split(",")[2])
        assertEquals("SPOILED", lines[1].split(",")[5])
        assertEquals("Some notes", lines[1].split(",")[7])
    }
}
