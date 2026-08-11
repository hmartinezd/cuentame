package com.miara.cuentame.feature.ingredients.csvimport.domain

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

    @Test
    fun `parse canonical columns accents quotes CRLF trailing cells and multiline values`() {
        val csv = "ingredient_name,sku,category,base_unit,count_unit,purchase_package,package_conversion_factor,default_area,supplier,vendor_item_code,current_cost_per_base_unit,reorder_point_base\r\n" +
            "\"Café, molido\",,Bebidas,lb,,\"Caja \"\"grande\"\"\",2,,Proveedor,,,\r\n" +
            "\"Línea\nNueva\",SKU2,,lb,,,,,,,,"

        val result = parser.parse(ByteArrayInputStream(csv.toByteArray())) as CsvParser.ParseResult.Success

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0][CsvParser.HEADER_INGREDIENT_NAME]).isEqualTo("Café, molido")
        assertThat(result.rows[0][CsvParser.HEADER_PURCHASE_PACKAGE]).isEqualTo("Caja \"grande\"")
        assertThat(result.rows[1][CsvParser.HEADER_INGREDIENT_NAME]).isEqualTo("Línea\nNueva")
        assertThat(result.rows[1][CsvParser.HEADER_REORDER_POINT]).isEmpty()
    }

    @Test
    fun `pad missing trailing values but reject extra values`() {
        val short = parser.parse(ByteArrayInputStream("ingredient_name,base_unit,sku\nTomato,lb".toByteArray())) as CsvParser.ParseResult.Success
        assertThat(short.rows.single()[CsvParser.HEADER_SKU]).isEmpty()

        val extra = parser.parse(ByteArrayInputStream("ingredient_name,base_unit\nTomato,lb,unexpected".toByteArray())) as CsvParser.ParseResult.Error
        assertThat(extra.type).isEqualTo(CsvParser.ParseErrorType.MALFORMED_CSV)
    }

    @Test
    fun `unknown column warning is typed`() {
        val result = parser.parse(ByteArrayInputStream("ingredient_name,base_unit,foo\nTomato,lb,x".toByteArray())) as CsvParser.ParseResult.Success
        assertThat(result.warnings).containsExactly(CsvParser.CsvParserWarning.UnknownColumn("foo"))
    }

    @Test
    fun `accept exactly maximum rows`() {
        val csv = buildString {
            append("ingredient_name,base_unit\n")
            repeat(CsvParser.MAX_ROWS) { append("Item $it,lb\n") }
        }
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray())) as CsvParser.ParseResult.Success
        assertThat(result.rows).hasSize(CsvParser.MAX_ROWS)
    }

    @Test
    fun `blank lines are ignored and whitespace-only file is empty`() {
        val valid = parser.parse(ByteArrayInputStream("ingredient_name,base_unit\n\nTomato,lb\n\n".toByteArray())) as CsvParser.ParseResult.Success
        assertThat(valid.rows).hasSize(1)
        val blank = parser.parse(ByteArrayInputStream("  \n \t".toByteArray())) as CsvParser.ParseResult.Error
        assertThat(blank.type).isEqualTo(CsvParser.ParseErrorType.EMPTY_FILE)
    }
}
