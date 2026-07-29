package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupStorageFailure
import com.miara.cuentame.core.backup.platform.DefaultBackupStorageErrorClassifier
import org.junit.Test
import java.io.IOException

/**
 * Tests for storage-error detection in [DefaultBackupStorageErrorClassifier].
 */
class InsufficientStorageDetectionTest {

    private val classifier = DefaultBackupStorageErrorClassifier()

    @Test
    fun `direct ENOSPC IOException is detected`() {
        val e = IOException("ENOSPC: write failed")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.InsufficientSpace)
    }

    @Test
    fun `nested ENOSPC IOException is detected`() {
        val inner = IOException("ENOSPC")
        val outer = RuntimeException("Wrapper", inner)
        assertThat(classifier.classify(outer)).isEqualTo(BackupStorageFailure.InsufficientSpace)
    }

    @Test
    fun `No space left on device message is detected`() {
        val e = IOException("No space left on device")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.InsufficientSpace)
    }

    @Test
    fun `unrelated IOException is detected as GenericIo`() {
        val e = IOException("File not found")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.GenericIo)
    }

    @Test
    fun `unrelated RuntimeException is detected as GenericIo`() {
        val e = RuntimeException("Some random error")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.GenericIo)
    }

    @Test
    fun `SecurityException is detected as PermissionDenied`() {
        val e = SecurityException("Permission denied")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.PermissionDenied)
    }

    @Test
    fun `nested SecurityException is detected as PermissionDenied`() {
        val inner = SecurityException("Permission denied")
        val outer = RuntimeException("Wrapper", inner)
        assertThat(classifier.classify(outer)).isEqualTo(BackupStorageFailure.PermissionDenied)
    }
}
