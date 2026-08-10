package com.miara.cuentame.feature.ingredient.import.domain

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream
import java.io.PushbackInputStream
import javax.inject.Inject

class CsvParser @Inject constructor() {

    fun parse(inputStream: InputStream): ParseResult {
        return try {
            val limitedStream = LimitedInputStream(inputStream, MAX_FILE_SIZE)
            val pushbackStream = PushbackInputStream(limitedStream, 3)
            
            // Consume UTF-8 BOM if present
            val bom = ByteArray(3)
            val read = pushbackStream.read(bom)
            if (read == 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
                // BOM consumed
            } else if (read > 0) {
                pushbackStream.unread(bom, 0, read)
            }

            val reader = csvReader {
                skipEmptyLine = true
            }

            val rows = reader.readAllWithHeader(pushbackStream)
            
            if (rows.isEmpty()) {
                return ParseResult.Error(ParseErrorType.EMPTY_FILE)
            }

            if (rows.size > MAX_ROWS) {
                return ParseResult.Error(ParseErrorType.TOO_MANY_ROWS)
            }

            // Validate headers (flexible matching)
            val firstRow = rows.first()
            val rawHeaders = firstRow.keys
            val normalizedHeaders = rawHeaders.map { it.trim().lowercase() }
            
            if (normalizedHeaders.size != rawHeaders.size) {
                return ParseResult.Error(ParseErrorType.DUPLICATE_HEADERS)
            }

            val missingRequired = REQUIRED_HEADERS.filter { required ->
                normalizedHeaders.none { it == required }
            }

            if (missingRequired.isNotEmpty()) {
                return ParseResult.Error(ParseErrorType.MISSING_HEADERS, "Missing required columns: ${missingRequired.joinToString()}")
            }

            // Remap rows to canonical headers
            val remappedRows = rows.map { row ->
                row.entries.associate { (key, value) ->
                    val canonicalKey = CANONICAL_HEADERS.find { it == key.trim().lowercase() } ?: key
                    canonicalKey to value
                }
            }

            ParseResult.Success(remappedRows)
        } catch (e: Exception) {
            if (e is LimitExceededException) {
                ParseResult.Error(ParseErrorType.FILE_TOO_LARGE)
            } else {
                ParseResult.Error(ParseErrorType.MALFORMED_CSV, e.message)
            }
        }
    }

    sealed class ParseResult {
        data class Success(
            val rows: List<Map<String, String>>,
            val warnings: List<String> = emptyList()
        ) : ParseResult()
        data class Error(val type: ParseErrorType, val message: String? = null) : ParseResult()
    }

    enum class ParseErrorType {
        FILE_TOO_LARGE,
        TOO_MANY_ROWS,
        EMPTY_FILE,
        MISSING_HEADERS,
        DUPLICATE_HEADERS,
        MALFORMED_CSV,
        READ_FAILURE
    }

    companion object {
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        const val MAX_ROWS = 5000

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

        private val REQUIRED_HEADERS = listOf(HEADER_INGREDIENT_NAME, HEADER_BASE_UNIT)
        private val CANONICAL_HEADERS = listOf(
            HEADER_INGREDIENT_NAME, HEADER_SKU, HEADER_CATEGORY, HEADER_BASE_UNIT,
            HEADER_COUNT_UNIT, HEADER_PURCHASE_PACKAGE, HEADER_PACKAGE_CONVERSION,
            HEADER_DEFAULT_AREA, HEADER_SUPPLIER, HEADER_VENDOR_CODE,
            HEADER_CURRENT_COST, HEADER_REORDER_POINT
        )
    }

    private class LimitedInputStream(private val inner: InputStream, private val limit: Long) : InputStream() {
        private var bytesRead = 0L
        override fun read(): Int {
            if (bytesRead >= limit) throw LimitExceededException()
            val b = inner.read()
            if (b != -1) bytesRead++
            return b
        }
    }

    private class LimitExceededException : Exception()
}
