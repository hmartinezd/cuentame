package com.venkoi.cuentame.feature.ingredients.csvimport.domain

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

        csvWriter().writeAll(listOf(headers), outputStream)
    }
}
