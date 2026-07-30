package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveEntryValidatorTest {

    @Test
    fun `valid canonical core paths are safe`() {
        assertThat(ArchiveEntryValidator.isSafe("manifest.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("data/database.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("preferences/settings.json")).isTrue()
    }

    @Test
    fun `valid canonical attachment paths are safe`() {
        assertThat(ArchiveEntryValidator.isSafe("attachments/0123456789abcdef/photo.jpg")).isTrue()
    }

    @Test
    fun `leading slash is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("/manifest.json")).isFalse()
    }

    @Test
    fun `trailing slash is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("manifest.json/")).isFalse()
    }

    @Test
    fun `backslash is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("data\\database.json")).isFalse()
    }

    @Test
    fun `parent traversal is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("../manifest.json")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("data/../../etc/passwd")).isFalse()
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
    fun `windows drive prefix is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("C:/manifest.json")).isFalse()
    }

    @Test
    fun `blank name is unsafe`() {
        assertThat(ArchiveEntryValidator.isSafe("   ")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("")).isFalse()
    }

    @Test
    fun `overlong entry name is unsafe`() {
        val longName = "a".repeat(256)
        assertThat(ArchiveEntryValidator.isSafe(longName)).isFalse()
    }

    @Test
    fun `non-canonical normalization is unsafe`() {
        // Character 'e' with acute accent (U+00E9) in NFD (e + combining acute)
        // NFC is U+00E9
        val nfd = "e\u0301.jpg"
        assertThat(ArchiveEntryValidator.isSafe(nfd)).isFalse()
    }
}
