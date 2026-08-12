package com.miara.cuentame.feature.reports.export

import com.miara.cuentame.core.domain.repository.PurchaseExportRow
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseCsvExportTest {

    @Test
    fun `generate should include all columns`() {
        val rows = listOf(
            PurchaseExportRow(
                purchaseDate = 1723482246000L, // 2024-08-12T17:04:06Z
                supplierName = "Supplier A",
                invoiceNumber = "INV-001",
                ingredientName = "Tomato",
                quantityEntered = "10",
                purchaseUnitLabel = "Case",
                quantityBase = "50",
                baseUnitSymbol = "kg",
                unitCostBase = "2.5",
                lineTotal = "125.0",
                status = "POSTED"
            )
        )

        val csv = PurchaseCsvExport.generate(rows)
        val lines = csv.lines()

        assertEquals("purchase_date,supplier,document_number,ingredient,quantity,unit,quantity_base,unit_cost,line_total,status", lines[0])
        assertTrue(lines[1].contains("Supplier A"))
        assertTrue(lines[1].contains("INV-001"))
        assertTrue(lines[1].contains("Tomato"))
        assertTrue(lines[1].contains("POSTED"))
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
