package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveEntryValidatorTest {

    @Test
    fun `isSafe rejects absolute paths`() {
        assertThat(ArchiveEntryValidator.isSafe("/data/db.json")).isFalse()
    }

    @Test
    fun `isSafe rejects backslashes`() {
        assertThat(ArchiveEntryValidator.isSafe("data\\db.json")).isFalse()
    }

    @Test
    fun `isSafe rejects path traversal`() {
        assertThat(ArchiveEntryValidator.isSafe("data/../../etc/passwd")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("../manifest.json")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("data/./db.json")).isFalse()
    }

    @Test
    fun `isSafe accepts relative simple paths`() {
        assertThat(ArchiveEntryValidator.isSafe("manifest.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("data/database.json")).isTrue()
        assertThat(ArchiveEntryValidator.isSafe("attachments/1/image.jpg")).isTrue()
    }

    @Test
    fun `sanitize cleans up common issues`() {
        assertThat(ArchiveEntryValidator.sanitize("\\data\\db.json")).isEqualTo("data/db.json")
        assertThat(ArchiveEntryValidator.sanitize("//data///db.json")).isEqualTo("data/db.json")
    }
}
