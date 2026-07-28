package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChecksumParserTest {

    private val validHash1 = "a".repeat(64)
    private val validHash2 = "b".repeat(64)

    @Test
    fun parse_validJsonObject_returnsMap() {
        val json = """
            {
              "database.json": "$validHash1",
              "manifest.json": "$validHash2"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isSuccess).isTrue()
        val map = result.getOrThrow()
        assertThat(map).hasSize(2)
        assertThat(map["database.json"]).isEqualTo(validHash1)
        assertThat(map["manifest.json"]).isEqualTo(validHash2)
    }

    @Test
    fun parse_escapedKeys_decodesAndDetectsDuplicates() {
        // Escaped unicode dot in key
        val json = """
            {
              "database.json": "$validHash1",
              "database\u002ejson": "$validHash2"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Duplicate key detected")
    }

    @Test
    fun parse_selfReferentialChecksumsKey_isRejected() {
        val json = """
            {
              "checksums.json": "$validHash1"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("checksums.json self-referential key forbidden")
    }

    @Test
    fun parse_emptyObject_isRejected() {
        val result = ChecksumParser.parse("{}")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksums object cannot be empty")
    }

    @Test
    fun parse_nonStringValue_isRejected() {
        val json = """
            {
              "database.json": 12345
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_uppercaseOrInvalidHash_isRejected() {
        val uppercaseHash = "A".repeat(64)
        val json = """
            {
              "database.json": "$uppercaseHash"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid SHA-256 hash format")
    }

    @Test
    fun parse_trailingContent_isRejected() {
        val json = """
            {
              "database.json": "$validHash1"
            }
            extra_content
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Unexpected trailing content")
    }

    @Test
    fun parse_malformedEscapeSequence_isRejected() {
        val json = """
            {
              "database\zjson": "$validHash1"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid escape character")
    }
}
