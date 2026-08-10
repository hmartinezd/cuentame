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
    fun `parse with quoted comma`() {
        val csv = """
            ingredient_name,base_unit,category
            "Tomato, Red",lb,Produce
        """.trimIndent()
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        val rows = (result as CsvParser.ParseResult.Success).rows
        assertThat(rows[0]["ingredient_name"]).isEqualTo("Tomato, Red")
    }

    @Test
    fun `error on missing required header`() {
        val csv = """
            ingredient_name,sku
            Tomato,TOM001
        """.trimIndent()
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        assertThat(result).isInstanceOf(CsvParser.ParseResult.Error::class.java)
        assertThat((result as CsvParser.ParseResult.Error).message).contains("base_unit")
    }

    @Test
    fun `handle CRLF and UTF-8`() {
        val csv = "ingredient_name,base_unit\r\nJalapeño,lb"
        
        val result = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        
        val rows = (result as CsvParser.ParseResult.Success).rows
        assertThat(rows[0]["ingredient_name"]).isEqualTo("Jalapeño")
    }
}
