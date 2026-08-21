package com.venkoi.restaurantops.feature.counts.export

import com.venkoi.restaurantops.core.common.util.CsvWriter
import com.venkoi.restaurantops.core.domain.repository.StockCountExportRow
import com.venkoi.restaurantops.core.model.count.StockCount
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
            val expected = parseOptionalHistoricalDecimal(row.expectedQuantityBase)
            val counted = parseRequiredHistoricalDecimal(row.countedQuantityBase)
            val adjustment = parseOptionalHistoricalDecimal(row.adjustmentQuantityBase)
            
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
                CsvWriter.formatNumber(adjustment),
                row.notes.orEmpty()
            )))
        }
    }

    private fun parseRequiredHistoricalDecimal(value: String): BigDecimal {
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw com.venkoi.restaurantops.core.domain.validation.ValidationError.MalformedStockCountMovementHistory
        }
    }

    private fun parseOptionalHistoricalDecimal(value: String?): BigDecimal? {
        if (value == null) return null
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw com.venkoi.restaurantops.core.domain.validation.ValidationError.MalformedStockCountMovementHistory
        }
    }
}
