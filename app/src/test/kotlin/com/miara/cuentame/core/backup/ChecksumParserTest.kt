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
    fun parse_leadingAndTrailingWhitespace_succeeds() {
        val json = "  \n\t {\n \"data/db.json\": \"$validHash1\" \n} \n "
        val result = ChecksumParser.parse(json)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun parse_escapedSlashAndBackslash_succeeds() {
        val json = """
            {
              "attachments\/att1.jpg": "$validHash1",
              "attachments\\att2.jpg": "$validHash2"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isSuccess).isTrue()
        val map = result.getOrThrow()
        assertThat(map["attachments/att1.jpg"]).isEqualTo(validHash1)
        assertThat(map["attachments\\att2.jpg"]).isEqualTo(validHash2)
    }

    @Test
    fun parse_normalDuplicateKey_isRejected() {
        val json = """
            {
              "data/db.json": "$validHash1",
              "data/db.json": "$validHash2"
            }
        """.trimIndent()

        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Duplicate key detected")
    }

    @Test
    fun parse_unicodeEscapedDuplicateKey_isRejected() {
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
    fun parse_escapedSlashDuplicateKey_isRejected() {
        val json = """
            {
              "data/db.json": "$validHash1",
              "data\/db.json": "$validHash2"
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
    fun parse_emptyInput_isRejected() {
        val result = ChecksumParser.parse("")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksums JSON is empty")
    }

    @Test
    fun parse_emptyObject_isRejected() {
        val result = ChecksumParser.parse("{}")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksums object cannot be empty")
    }

    @Test
    fun parse_numberValue_isRejected() {
        val json = """{"database.json": 12345}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_booleanValue_isRejected() {
        val json = """{"database.json": true}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_nullValue_isRejected() {
        val json = """{"database.json": null}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_nestedObjectValue_isRejected() {
        val json = """{"database.json": {"hash": "$validHash1"}}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_arrayValue_isRejected() {
        val json = """{"database.json": ["$validHash1"]}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Checksum value must be a string")
    }

    @Test
    fun parse_hashTooShort_isRejected() {
        val json = """{"database.json": "abcd1234"}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid SHA-256 hash format")
    }

    @Test
    fun parse_hashTooLong_isRejected() {
        val json = """{"database.json": "${"a".repeat(65)}"}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid SHA-256 hash format")
    }

    @Test
    fun parse_uppercaseHash_isRejected() {
        val uppercaseHash = "A".repeat(64)
        val json = """{"database.json": "$uppercaseHash"}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid SHA-256 hash format")
    }

    @Test
    fun parse_trailingComma_isRejected() {
        val json = """
            {
              "database.json": "$validHash1",
            }
        """.trimIndent()
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
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
    fun parse_unterminatedString_isRejected() {
        val json = """{"database.json": "$validHash1"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun parse_invalidUnicodeEscape_isRejected() {
        val json = """{"data\u00ZZ.json": "$validHash1"}"""
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun parse_unescapedControlCharacter_isRejected() {
        val json = "{\"data\nb.json\": \"$validHash1\"}"
        val result = ChecksumParser.parse(json)
        assertThat(result.isFailure).isTrue()
    }
}
