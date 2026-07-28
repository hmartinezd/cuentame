package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AttachmentFilenameSanitizerTest {

    @Test
    fun `sanitize removes path separators`() {
        assertThat(AttachmentFilenameSanitizer.sanitize("dir/file.jpg")).isEqualTo("dir_file.jpg")
        assertThat(AttachmentFilenameSanitizer.sanitize("dir\\file.jpg")).isEqualTo("dir_file.jpg")
    }

    @Test
    fun `sanitize removes control characters`() {
        assertThat(AttachmentFilenameSanitizer.sanitize("file\u0000.jpg")).isEqualTo("file.jpg")
        assertThat(AttachmentFilenameSanitizer.sanitize("file\n.jpg")).isEqualTo("file.jpg")
    }

    @Test
    fun `sanitize collapses underscores and whitespace`() {
        assertThat(AttachmentFilenameSanitizer.sanitize("file   name.jpg")).isEqualTo("file name.jpg")
        assertThat(AttachmentFilenameSanitizer.sanitize("file___name.jpg")).isEqualTo("file_name.jpg")
    }

    @Test
    fun `sanitize handles dots safely`() {
        assertThat(AttachmentFilenameSanitizer.sanitize(".")).isEqualTo("attachment")
        assertThat(AttachmentFilenameSanitizer.sanitize("..")).isEqualTo("attachment")
        assertThat(AttachmentFilenameSanitizer.sanitize("...")).isEqualTo("attachment")
    }

    @Test
    fun `sanitize enforces maximum length while preserving extension`() {
        val longBase = "a".repeat(150)
        val sanitized = AttachmentFilenameSanitizer.sanitize("$longBase.jpg")
        assertThat(sanitized.length).isEqualTo(128)
        assertThat(sanitized.endsWith(".jpg")).isTrue()
    }
}
