package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveEntryValidatorTest {

    @Test
    fun `isSafe rejects absolute paths`() {
        assertThat(ArchiveEntryValidator.isSafe("/etc/passwd")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("C:\\Windows")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("C:/Windows")).isFalse()
    }

    @Test
    fun `isSafe rejects relative traversal`() {
        assertThat(ArchiveEntryValidator.isSafe("../outside.json")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("data/../../secret.txt")).isFalse()
    }

    @Test
    fun `isSafe accepts simple alphanumeric paths`() {
        assertThat(ArchiveEntryValidator.isSafe("data/database.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("preferences/settings.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("manifest.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("checksums.json")).isTrue()
    }

    @Test
    fun `isSafe accepts valid attachment paths`() {
        assertThat(ArchiveEntryValidator.isSafe("attachments/abc1234567890def/receipt.jpg")).isTrue()
    }

    @Test
    fun `isSafe rejects backslashes`() {
        assertThat(ArchiveEntryValidator.isSafe("data\\database.json")).isFalse()
    }

    @Test
    fun `isSafe rejects blank names`() {
        assertThat(ArchiveEntryValidator.isSafe("")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("  ")).isFalse()
    }

    @Test
    fun `trailing slash is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("manifest.json/")).isFalse()
    }

    @Test
    fun `current directory segment is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("./manifest.json")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("data/./database.json")).isFalse()
    }

    @Test
    fun `empty path segment is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("data//database.json")).isFalse()
    }

    @Test
    fun `overlong entry name is unsafe`() {
        val longName = "a".repeat(256)
        assertThat(ArchiveEntryValidator.isSafe(longName)).isFalse()
    }

    @Test
    fun `non-canonical normalization is unsafe`() {
        // NFC is U+00E9, NFD is e + combining acute
        val nfd = "e\u0301.jpg"
        assertThat(ArchiveEntryValidator.isSafe(nfd)).isFalse()
    }
}
