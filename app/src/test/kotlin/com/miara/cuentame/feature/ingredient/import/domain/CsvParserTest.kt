package com.miara.cuentame.feature.ingredient.import.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvParserTest {
    private val parser = CsvParser()

    @Test
    fun `parse valid minimal CSV`() {
        val csv = """
            ingredient_name,base_unit
            Tomato,lb
        """.trimIndent()
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Success::class.java)
        val rows = (result as CsvParser.ParseResult.Success).rows
        assertThat(rows).hasSize(1)
        assertThat(rows[0]["ingredient_name"]).isEqualTo("Tomato")
        assertThat(rows[0]["base_unit"]).isEqualTo("lb")
    }

    @Test
    fun `handle case-insensitive and trimmed headers`() {
        val csv = """
             Ingredient_Name , BASE_UNIT 
            Tomato,lb
        """.trimIndent()
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Success::class.java)
        val rows = (result as CsvParser.ParseResult.Success).rows
        assertThat(rows[0]["ingredient_name"]).isEqualTo("Tomato")
        assertThat(rows[0]["base_unit"]).isEqualTo("lb")
    }

    @Test
    fun `error on duplicate headers`() {
        val csv = "ingredient_name,base_unit,ingredient_name\nTomato,lb,Extra"
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Error::class.java)
        val error = result as CsvParser.ParseResult.Error
        assertThat(error.type).isEqualTo(CsvParser.ParseErrorType.DUPLICATE_HEADERS)
    }

    @Test
    fun `error on file too large`() {
        val largeContent = ByteArray(6 * 1024 * 1024) // 6MB
        val result = parser.parse(ByteArrayInputStream(largeContent))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Error::class.java)
        val error = result as CsvParser.ParseResult.Error
        assertThat(error.type).isEqualTo(CsvParser.ParseErrorType.FILE_TOO_LARGE)
    }

    @Test
    fun `error on too many rows`() {
        val sb = StringBuilder("ingredient_name,base_unit\n")
        repeat(5001) {
            sb.append("Item $it,lb\n")
        }
        val result = parser.parse(ByteArrayInputStream(sb.toString().toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Error::class.java)
        val error = result as CsvParser.ParseResult.Error
        assertThat(error.type).isEqualTo(CsvParser.ParseErrorType.TOO_MANY_ROWS)
    }

    @Test
    fun `handle UTF-8 BOM`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = "ingredient_name,base_unit\nTomato,lb".toByteArray()
        val combined = bom + csv
        
        val result = parser.parse(ByteArrayInputStream(combined))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Success::class.java)
        val rows = (result as CsvParser.ParseResult.Success).rows
        assertThat(rows[0]["ingredient_name"]).isEqualTo("Tomato")
    }

    @Test
    fun `return success for header-only file`() {
        val csv = "ingredient_name,base_unit"
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Success::class.java)
        assertThat((result as CsvParser.ParseResult.Success).rows).isEmpty()
    }
}
