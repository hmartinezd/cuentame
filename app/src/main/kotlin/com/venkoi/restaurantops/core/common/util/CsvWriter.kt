package com.venkoi.restaurantops.core.common.util

import java.math.BigDecimal

object CsvWriter {
    fun escape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun formatNumber(value: BigDecimal?): String {
        return value?.stripTrailingZeros()?.toPlainString().orEmpty()
    }

    fun writeRow(values: List<String>): String {
        return values.joinToString(",") { escape(it) }
    }
}
