package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AttachmentFilenameSanitizerTest {

    @Test
    fun `sanitize removes dangerous characters`() {
        assertThat(AttachmentFilenameSanitizer.sanitize("image/../..secret.jpg")).isEqualTo("image_.._..secret.jpg")
        assertThat(AttachmentFilenameSanitizer.sanitize("file*.png")).isEqualTo("file_.png")
        assertThat(AttachmentFilenameSanitizer.sanitize("my file.pdf")).isEqualTo("my file.pdf")
    }

    @Test
    fun `sanitize preserves extensions`() {
        assertThat(AttachmentFilenameSanitizer.sanitize("photo.jpeg")).isEqualTo("photo.jpeg")
    }

    @Test
    fun `isValid validates correctly`() {
        assertThat(AttachmentFilenameSanitizer.isValid("safe.jpg")).isTrue()
        assertThat(AttachmentFilenameSanitizer.isValid("../danger")).isFalse()
        assertThat(AttachmentFilenameSanitizer.isValid("spaces are fine.png")).isTrue()
        assertThat(AttachmentFilenameSanitizer.isValid("")).isFalse()
    }
}
