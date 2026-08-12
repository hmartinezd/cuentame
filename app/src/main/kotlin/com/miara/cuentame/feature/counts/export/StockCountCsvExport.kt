package com.miara.cuentame.feature.counts.export

import com.miara.cuentame.core.common.util.CsvWriter
import com.miara.cuentame.core.domain.repository.StockCountExportRow
import com.miara.cuentame.core.model.count.StockCount
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object StockCountCsvExport {
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(java.time.ZoneOffset.UTC)

    fun generate(count: StockCount, rows: List<StockCountExportRow>): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "count_id",
            "status",
            "effective_at",
            "completed_at",
            "voided_at",
            "area",
            "ingredient",
            "base_unit",
            "expected_quantity",
            "counted_quantity",
            "variance_quantity",
            "adjustment_quantity",
            "notes"
        )))

        rows.forEach { row ->
            val expected = row.expectedQuantityBase?.let { try { BigDecimal(it) } catch (e: Exception) { null } }
            val counted = try { BigDecimal(row.countedQuantityBase) } catch (e: Exception) { BigDecimal.ZERO }
            
            val variance = expected?.let { counted.subtract(it) }

            appendLine(CsvWriter.writeRow(listOf(
                count.id.value,
                count.status.name,
                dateFormatter.format(count.effectiveAt),
                count.completedAt?.let { dateFormatter.format(it) }.orEmpty(),
                count.voidedAt?.let { dateFormatter.format(it) }.orEmpty(),
                row.areaName.orEmpty(),
                row.ingredientName,
                row.baseUnitSymbol,
                CsvWriter.formatNumber(expected),
                CsvWriter.formatNumber(counted),
                CsvWriter.formatNumber(variance),
                row.adjustmentQuantityBase?.let { try { BigDecimal(it) } catch (e: Exception) { null } }.let { CsvWriter.formatNumber(it) },
                row.notes.orEmpty()
            )))
        }
    }
}
