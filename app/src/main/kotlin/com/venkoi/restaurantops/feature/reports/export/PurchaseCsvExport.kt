package com.venkoi.restaurantops.feature.reports.export

import com.venkoi.restaurantops.core.common.util.CsvWriter
import com.venkoi.restaurantops.core.domain.repository.PurchaseExportRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PurchaseCsvExport {
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(java.time.ZoneOffset.UTC)

    fun generate(rows: List<PurchaseExportRow>): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "purchase_date",
            "supplier",
            "document_number",
            "ingredient",
            "quantity",
            "unit",
            "quantity_base",
            "base_unit",
            "unit_cost_base",
            "line_total",
            "status"
        )))
        rows.forEach { row ->
            appendLine(CsvWriter.writeRow(listOf(
                dateFormatter.format(Instant.ofEpochMilli(row.purchaseDate)),
                row.supplierName.orEmpty(),
                row.invoiceNumber.orEmpty(),
                row.ingredientName,
                row.quantityEntered,
                row.purchaseUnitLabel.orEmpty(),
                row.quantityBase,
                row.baseUnitSymbol,
                row.unitCostBase,
                row.lineTotal,
                row.status
            )))
        }
    }
}
