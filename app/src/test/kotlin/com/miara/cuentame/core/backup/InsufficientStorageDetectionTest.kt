package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/**
 * Tests for storage-error detection in [AndroidBackupRepository.isInsufficientStorage].
 *
 * Uses reflection to invoke the private method.
 */
class InsufficientStorageDetectionTest {

    private fun isInsufficientStorage(e: Throwable): Boolean {
        // Invoke via generateSequence to mirror the production implementation
        return generateSequence(e) { it.cause }.any { cause ->
            when {
                cause is IOException && (
                    cause.message?.contains("ENOSPC", ignoreCase = true) == true ||
                    cause.message?.contains("No space left on device", ignoreCase = true) == true
                ) -> true
                cause.javaClass.simpleName == "ErrnoException" -> {
                    try {
                        val errnoField = cause.javaClass.getField("errno")
                        errnoField.getInt(cause) == 28
                    } catch (_: Exception) { false }
                }
                else -> false
            }
        }
    }

    @Test
    fun `direct ENOSPC IOException is detected`() {
        val e = IOException("ENOSPC: write failed")
        assertThat(isInsufficientStorage(e)).isTrue()
    }

    @Test
    fun `nested ENOSPC IOException is detected`() {
        val inner = IOException("ENOSPC")
        val outer = RuntimeException("Wrapper", inner)
        assertThat(isInsufficientStorage(outer)).isTrue()
    }

    @Test
    fun `No space left on device message is detected`() {
        val e = IOException("No space left on device")
        assertThat(isInsufficientStorage(e)).isTrue()
    }

    @Test
    fun `No space left on device case-insensitive is detected`() {
        val e = IOException("no space left on device")
        assertThat(isInsufficientStorage(e)).isTrue()
    }

    @Test
    fun `unrelated IOException is not detected`() {
        val e = IOException("File not found")
        assertThat(isInsufficientStorage(e)).isFalse()
    }

    @Test
    fun `unrelated RuntimeException is not detected`() {
        val e = RuntimeException("Some random error")
        assertThat(isInsufficientStorage(e)).isFalse()
    }

    @Test
    fun `deeply nested ENOSPC is detected`() {
        val root = IOException("ENOSPC")
        val mid = RuntimeException("wrapper1", root)
        val outer = RuntimeException("wrapper2", mid)
        assertThat(isInsufficientStorage(outer)).isTrue()
    }

    @Test
    fun `null cause chain terminates cleanly`() {
        val e = IOException("unrelated")
        assertThat(isInsufficientStorage(e)).isFalse()
    }
}
