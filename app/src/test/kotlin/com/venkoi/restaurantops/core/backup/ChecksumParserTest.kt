package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChecksumParserTest {

    @Test
    fun `parse empty input fails`() {
        assertThat(ChecksumParser.parse("").isFailure).isTrue()
    }

    @Test
    fun `parse empty object fails`() {
        assertThat(ChecksumParser.parse("{}").isFailure).isTrue()
    }

    @Test
    fun `parse valid sorted object succeeds`() {
        val json = """
            {
                "data/database.json": "0000000000000000000000000000000000000000000000000000000000000000",
                "manifest.json": "1111111111111111111111111111111111111111111111111111111111111111"
            }
        """.trimIndent()
        val result = ChecksumParser.parse(json)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).hasSize(2)
    }

    @Test
    fun `parse duplicate key fails`() {
        val json = """{"a":"0000000000000000000000000000000000000000000000000000000000000000", "a":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `parse non-hex hash fails`() {
        val json = """{"a":"not-a-hex-value"}"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `parse self reference fails`() {
        val json = """{"checksums.json":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `parse malformed escape fails`() {
        val json = """{"a\z":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `parse trailing comma fails`() {
        val json = """{"a":"0000000000000000000000000000000000000000000000000000000000000000",}"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `parse trailing content fails`() {
        val json = """{"a":"0000000000000000000000000000000000000000000000000000000000000000"} garbage"""
        assertThat(ChecksumParser.parse(json).isFailure).isTrue()
    }
}
