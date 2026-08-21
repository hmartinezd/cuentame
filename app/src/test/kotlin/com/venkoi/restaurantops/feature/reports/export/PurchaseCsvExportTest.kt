package com.venkoi.restaurantops.feature.reports.export

import com.venkoi.restaurantops.core.domain.repository.PurchaseExportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseCsvExportTest {

    @Test
    fun `generate should include all columns and clarify base unit context`() {
        val rows = listOf(
            PurchaseExportRow(
                purchaseDate = 1723482246000L, // 2024-08-12T17:04:06Z
                supplierName = "Supplier, Inc.",
                invoiceNumber = "INV-001",
                ingredientName = "Tomato",
                quantityEntered = "2",
                purchaseUnitLabel = "Case",
                quantityBase = "48",
                baseUnitSymbol = "each",
                unitCostBase = "0.35",
                lineTotal = "16.8",
                status = "POSTED"
            )
        )

        val csv = PurchaseCsvExport.generate(rows)
        val lines = csv.lines()

        val header = "purchase_date,supplier,document_number,ingredient,quantity,unit,quantity_base,base_unit,unit_cost_base,line_total,status"
        assertEquals(header, lines[0])
        
        val row = lines[1]
        assertTrue(row.contains("\"Supplier, Inc.\""))
        assertTrue(row.contains("Case"))
        assertTrue(row.contains("48"))
        assertTrue(row.contains("each"))
        assertTrue(row.contains("0.35"))
        assertTrue(row.contains("16.8"))
        assertTrue(row.contains("POSTED"))
    }
}
