package com.venkoi.restaurantops.feature.ingredients.csvimport.domain

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.context.ExcessFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.context.InsufficientFieldsRowBehaviour
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

        val cells = csvReader {
            skipEmptyLine = true
            insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
            excessFieldsRowBehaviour = ExcessFieldsRowBehaviour.ERROR
        }.readAll(stream)
        if (cells.isEmpty()) return ParseResult.Error(ParseErrorType.EMPTY_FILE)

        val headers = cells.first().map { it.trim() }
        if (headers.all { it.isBlank() }) return ParseResult.Error(ParseErrorType.EMPTY_FILE)
        if (headers.size != headers.toSet().size) return ParseResult.Error(ParseErrorType.DUPLICATE_HEADERS)

        val dataRows = cells.drop(1)
        if (dataRows.size > MAX_ROWS) return ParseResult.Error(ParseErrorType.TOO_MANY_ROWS)

        if (dataRows.any { it.size > headers.size }) return ParseResult.Error(ParseErrorType.MALFORMED_CSV)
        ParseResult.Success(
            CsvSourceTable(
                columns = headers.mapIndexed { index, header -> CsvSourceColumn(index, header) },
                rows = dataRows.map { values -> headers.indices.map { index -> values.getOrElse(index) { "" } } }
            )
        )
    } catch (_: LimitExceededException) {
        ParseResult.Error(ParseErrorType.FILE_TOO_LARGE)
    } catch (_: IOException) {
        ParseResult.Error(ParseErrorType.READ_FAILURE)
        } catch (_: Exception) {
            ParseResult.Error(ParseErrorType.MALFORMED_CSV)
        }
    }

    sealed class ParseResult {
        data class Success(val table: CsvSourceTable, val warnings: List<CsvParserWarning> = emptyList()) : ParseResult() {
            @Deprecated("Map source columns explicitly")
            val rows: List<Map<String, String>> get() = table.rows.map { row ->
                table.columns.associate { it.header.trim().lowercase() to row.getOrElse(it.index) { "" } }
            }

            constructor(rows: List<Map<String, String>>, warnings: List<CsvParserWarning> = emptyList()) : this(
                CsvSourceTable(
                    (linkedSetOf(HEADER_INGREDIENT_NAME, HEADER_BASE_UNIT) + rows.firstOrNull()?.keys.orEmpty())
                        .mapIndexed { index, header -> CsvSourceColumn(index, header) },
                    rows.map { row ->
                        (linkedSetOf(HEADER_INGREDIENT_NAME, HEADER_BASE_UNIT) + rows.firstOrNull()?.keys.orEmpty())
                            .map { header -> row[header].orEmpty() }
                    }
                ), warnings
            )
        }
        data class Error(val type: ParseErrorType, val message: String? = null) : ParseResult()
    }

    enum class ParseErrorType { FILE_TOO_LARGE, TOO_MANY_ROWS, EMPTY_FILE, @Deprecated("Header mapping is handled after parsing") MISSING_HEADERS, DUPLICATE_HEADERS, MALFORMED_CSV, READ_FAILURE }
    sealed interface CsvParserWarning {
        @Deprecated("Unknown source columns are expected")
        data class UnknownColumn(val column: String) : CsvParserWarning
    }

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
