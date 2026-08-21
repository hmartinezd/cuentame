package com.venkoi.restaurantops.feature.reports.export

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.model.dashboard.InventoryDetailItem
import com.venkoi.restaurantops.core.model.dashboard.InventoryDetailReport
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InventoryCsvExportTest {

    @Test
    fun `generate should include all columns and handle missing cost`() {
        val rows = listOf(
            InventoryDetailItem(
                ingredientId = IngredientId("i1"),
                ingredientName = "Tomato",
                baseUnitSymbol = "kg",
                totalQuantityBase = BigDecimal("10.5"),
                currentAverageCost = BigDecimal("2.50"),
                currentInventoryValue = BigDecimal("26.25"),
                stockedAreaCount = 1,
                negativeAreaBalanceCount = 0,
                isMissingCost = false
            ),
            InventoryDetailItem(
                ingredientId = IngredientId("i2"),
                ingredientName = "Onion",
                baseUnitSymbol = "kg",
                totalQuantityBase = BigDecimal("5.0"),
                currentAverageCost = null,
                currentInventoryValue = null,
                stockedAreaCount = 1,
                negativeAreaBalanceCount = 1,
                isMissingCost = true
            )
        )
        val report = InventoryDetailReport(
            rows = rows,
            totalValue = BigDecimal("26.25"),
            recordCount = 2,
            valuedIngredientCount = 1,
            stockedIngredientCount = 2,
            missingCostCount = 1,
            negativeBalanceCount = 1
        )

        val csv = InventoryCsvExport.generate(report)
        val lines = csv.lines()

        // Header
        assertEquals("ingredient_name,base_unit,current_quantity_base,average_unit_cost,extended_value,cost_known,negative_balance", lines[0])
        
        // Row 1
        assertEquals("Tomato,kg,10.5,2.5,26.25,true,false", lines[1])
        
        // Row 2
        assertEquals("Onion,kg,5,,,false,true", lines[2])
    }

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
