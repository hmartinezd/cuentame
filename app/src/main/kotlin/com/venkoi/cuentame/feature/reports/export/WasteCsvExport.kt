package com.venkoi.cuentame.feature.reports.export

import com.venkoi.cuentame.core.common.util.CsvWriter
import com.venkoi.cuentame.core.model.dashboard.WasteDetailReport
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WasteCsvExport {
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(java.time.ZoneOffset.UTC)

    fun generate(report: WasteDetailReport): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "timestamp",
            "ingredient",
            "area",
            "quantity",
            "base_unit",
            "reason",
            "waste_value",
            "notes"
        )))
        report.rows.forEach { row ->
            appendLine(CsvWriter.writeRow(listOf(
                dateFormatter.format(row.timestamp),
                row.ingredientName,
                row.areaName,
                CsvWriter.formatNumber(row.quantityBase),
                row.baseUnitSymbol,
                row.reason.name,
                CsvWriter.formatNumber(row.historicalValue),
                row.notes.orEmpty()
            )))
        }
    }
}
