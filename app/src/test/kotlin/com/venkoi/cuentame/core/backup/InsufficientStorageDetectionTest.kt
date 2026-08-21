package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupDocumentOpenException
import com.venkoi.cuentame.core.backup.api.BackupDocumentOperation
import com.venkoi.cuentame.core.backup.api.BackupStorageFailure
import com.venkoi.cuentame.core.backup.platform.DefaultBackupStorageErrorClassifier
import org.junit.Test
import java.io.IOException

class InsufficientStorageDetectionTest {

    private val classifier = DefaultBackupStorageErrorClassifier()

    @Test
    fun `detects ENOSPC from message`() {
        val e = IOException("No space left on device (ENOSPC)")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.InsufficientSpace)
    }

    @Test
    fun `detects SecurityException as PermissionDenied`() {
        val e = SecurityException("Permission denied")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.PermissionDenied)
    }

    @Test
    fun `detects open failure as DestinationUnavailable`() {
        val e = BackupDocumentOpenException(BackupDocumentOperation.WRITE)
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.DestinationUnavailable)
    }

    @Test
    fun `falls back to GenericIo for generic IOException`() {
        val e = IOException("Disk error")
        assertThat(classifier.classify(e)).isEqualTo(BackupStorageFailure.GenericIo)
    }
}
