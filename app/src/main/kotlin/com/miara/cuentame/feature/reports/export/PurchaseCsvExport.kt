package com.miara.cuentame.feature.reports.export

import com.miara.cuentame.core.common.util.CsvWriter
import com.miara.cuentame.core.domain.repository.PurchaseExportRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PurchaseCsvExport {
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    fun generate(rows: List<PurchaseExportRow>): String = buildString {
        appendLine(CsvWriter.writeRow(listOf(
            "purchase_date",
            "supplier",
            "document_number",
            "ingredient",
            "quantity",
            "unit",
            "quantity_base",
            "unit_cost",
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
                row.unitCostBase,
                row.lineTotal,
                row.status
            )))
        }
    }
}
