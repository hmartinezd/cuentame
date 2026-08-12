package com.miara.cuentame.feature.reports.export

import com.miara.cuentame.core.common.util.CsvWriter
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport

object InventoryCsvExport {
    fun generate(report: InventoryDetailReport): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "ingredient_name",
            "base_unit",
            "current_quantity_base",
            "average_unit_cost",
            "extended_value",
            "cost_known",
            "negative_balance"
        )))
        report.rows.forEach { row ->
            appendLine(CsvWriter.writeRow(listOf(
                row.ingredientName,
                row.baseUnitSymbol,
                CsvWriter.formatNumber(row.totalQuantityBase),
                CsvWriter.formatNumber(row.currentAverageCost),
                CsvWriter.formatNumber(row.currentInventoryValue),
                (!row.isMissingCost).toString(),
                (row.negativeAreaBalanceCount > 0).toString()
            )))
        }
    }
}
