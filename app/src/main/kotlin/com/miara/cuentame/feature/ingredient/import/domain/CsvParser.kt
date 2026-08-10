package com.miara.cuentame.feature.ingredient.import.domain

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream
import javax.inject.Inject

class CsvParser @Inject constructor() {
    companion object {
        const val HEADER_INGREDIENT_NAME = "ingredient_name"
        const val HEADER_SKU = "sku"
        const val HEADER_CATEGORY = "category"
        const val HEADER_BASE_UNIT = "base_unit"
        const val HEADER_COUNT_UNIT = "count_unit"
        const val HEADER_PURCHASE_PACKAGE = "purchase_package"
        const val HEADER_PACKAGE_CONVERSION = "package_conversion_factor"
        const val HEADER_DEFAULT_AREA = "default_area"
        const val HEADER_SUPPLIER = "supplier"
        const val HEADER_VENDOR_CODE = "vendor_item_code"
        const val HEADER_CURRENT_COST = "current_cost_per_base_unit"
        const val HEADER_REORDER_POINT = "reorder_point_base"

        val REQUIRED_HEADERS = listOf(
            HEADER_INGREDIENT_NAME,
            HEADER_BASE_UNIT
        )
    }

    sealed class ParseResult {
        data class Success(val rows: List<Map<String, String>>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun parse(inputStream: InputStream): ParseResult {
        return try {
            val rows = csvReader {
                skipEmptyLine = true
            }.readAllWithHeader(inputStream)

            if (rows.isEmpty()) {
                return ParseResult.Error("CSV file is empty")
            }

            val firstRow = rows.first()
            val headers = firstRow.keys
            
            val missingHeaders = REQUIRED_HEADERS.filter { it !in headers }
            if (missingHeaders.isNotEmpty()) {
                return ParseResult.Error("Missing required headers: ${missingHeaders.joinToString(", ")}")
            }

            ParseResult.Success(rows)
        } catch (e: Exception) {
            ParseResult.Error("Failed to parse CSV: ${e.message}")
        }
    }
}
