package com.miara.cuentame.feature.ingredient.import.domain

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.OutputStream
import javax.inject.Inject

class CsvTemplateGenerator @Inject constructor() {
    fun generate(outputStream: OutputStream) {
        val headers = listOf(
            CsvParser.HEADER_INGREDIENT_NAME,
            CsvParser.HEADER_SKU,
            CsvParser.HEADER_CATEGORY,
            CsvParser.HEADER_BASE_UNIT,
            CsvParser.HEADER_COUNT_UNIT,
            CsvParser.HEADER_PURCHASE_PACKAGE,
            CsvParser.HEADER_PACKAGE_CONVERSION,
            CsvParser.HEADER_DEFAULT_AREA,
            CsvParser.HEADER_SUPPLIER,
            CsvParser.HEADER_VENDOR_CODE,
            CsvParser.HEADER_CURRENT_COST,
            CsvParser.HEADER_REORDER_POINT
        )

        val exampleRows = listOf(
            listOf(
                "Tomato", "TOM001", "Produce", "lb", "lb", "Case 25 lb", "25", "Walk-in Cooler", "Sysco", "12345", "1.42", "10"
            ),
            listOf(
                "Milk", "MILK01", "Dairy", "gal", "gal", "Crate 4 gal", "4", "Walk-in Cooler", "Local Dairy", "MD-100", "4.50", "2"
            )
        )

        csvWriter().writeAll(listOf(headers) + exampleRows, outputStream)
    }
}
