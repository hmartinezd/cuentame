package com.miara.cuentame.feature.counts.export

import com.miara.cuentame.core.common.util.CsvWriter
import com.miara.cuentame.core.domain.repository.StockCountExportRow
import com.miara.cuentame.core.model.count.StockCount
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object StockCountCsvExport {
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    fun generate(count: StockCount, rows: List<StockCountExportRow>): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "count_id",
            "completed_at",
            "area",
            "ingredient",
            "base_unit",
            "expected_quantity",
            "counted_quantity",
            "variance_quantity",
            "adjustment_quantity",
            "notes"
        )))
        val completedAt = count.effectiveAt
        rows.forEach { row ->
            appendLine(CsvWriter.writeRow(listOf(
                count.id.value,
                dateFormatter.format(completedAt),
                row.areaName.orEmpty(),
                row.ingredientName,
                row.baseUnitSymbol,
                row.expectedQuantityBase.orEmpty(),
                row.countedQuantityBase,
                row.adjustmentQuantityBase.orEmpty(),
                row.adjustmentQuantityBase.orEmpty(),
                row.notes.orEmpty()
            )))
        }
    }
}
