package com.miara.cuentame.feature.ingredients.csvimport.domain

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import javax.inject.Inject

class CsvParser @Inject constructor() {
    fun parse(inputStream: InputStream): ParseResult {
        return try {
        val stream = PushbackInputStream(LimitedInputStream(inputStream, MAX_FILE_SIZE), 3)
        val bom = ByteArray(3)
        val bomCount = stream.read(bom)
        if (!(bomCount == 3 && bom.contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) && bomCount > 0) {
            stream.unread(bom, 0, bomCount)
        }

        val cells = csvReader { skipEmptyLine = true }.readAll(stream)
        if (cells.isEmpty()) return ParseResult.Error(ParseErrorType.EMPTY_FILE)

        val headers = cells.first().map { it.trim().lowercase() }
        if (headers.size != headers.toSet().size) return ParseResult.Error(ParseErrorType.DUPLICATE_HEADERS)
        if (!headers.containsAll(REQUIRED_HEADERS)) return ParseResult.Error(ParseErrorType.MISSING_HEADERS)

        val dataRows = cells.drop(1)
        if (dataRows.size > MAX_ROWS) return ParseResult.Error(ParseErrorType.TOO_MANY_ROWS)

        val rows = dataRows.map { values ->
            headers.mapIndexed { index, header -> header to values.getOrElse(index) { "" } }.toMap()
        }
        val warnings = headers.filterNot(CANONICAL_HEADERS::contains).distinct().map { "Unknown CSV column: $it" }
        ParseResult.Success(rows, warnings)
    } catch (_: LimitExceededException) {
        ParseResult.Error(ParseErrorType.FILE_TOO_LARGE)
    } catch (_: IOException) {
        ParseResult.Error(ParseErrorType.READ_FAILURE)
        } catch (_: Exception) {
            ParseResult.Error(ParseErrorType.MALFORMED_CSV)
        }
    }

    sealed class ParseResult {
        data class Success(val rows: List<Map<String, String>>, val warnings: List<String> = emptyList()) : ParseResult()
        data class Error(val type: ParseErrorType, val message: String? = null) : ParseResult()
    }

    enum class ParseErrorType { FILE_TOO_LARGE, TOO_MANY_ROWS, EMPTY_FILE, MISSING_HEADERS, DUPLICATE_HEADERS, MALFORMED_CSV, READ_FAILURE }

    companion object {
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L
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
        private val CANONICAL_HEADERS = listOf(HEADER_INGREDIENT_NAME, HEADER_SKU, HEADER_CATEGORY, HEADER_BASE_UNIT, HEADER_COUNT_UNIT, HEADER_PURCHASE_PACKAGE, HEADER_PACKAGE_CONVERSION, HEADER_DEFAULT_AREA, HEADER_SUPPLIER, HEADER_VENDOR_CODE, HEADER_CURRENT_COST, HEADER_REORDER_POINT)
    }

    private class LimitedInputStream(private val inner: InputStream, private val limit: Long) : InputStream() {
        private var bytesRead = 0L
        override fun read(): Int {
            if (bytesRead == limit) {
                if (inner.read() == -1) return -1
                throw LimitExceededException()
            }
            return inner.read().also { if (it != -1) bytesRead++ }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (bytesRead == limit) {
                if (inner.read() == -1) return -1
                throw LimitExceededException()
            }
            val count = inner.read(buffer, offset, minOf(length.toLong(), limit - bytesRead).toInt())
            if (count > 0) bytesRead += count
            return count
        }
    }

    private class LimitExceededException : Exception()
}
