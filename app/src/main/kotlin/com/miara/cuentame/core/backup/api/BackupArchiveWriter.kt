package com.miara.cuentame.core.backup.api

import java.io.IOException
import java.io.OutputStream

sealed interface BackupArchiveWriteResult {
    data object Success : BackupArchiveWriteResult
    sealed interface Failure : BackupArchiveWriteResult {
        data object InvalidPlan : Failure
        data object AttachmentUnreadable : Failure
        data object AttachmentChanged : Failure
        data object LimitExceeded : Failure
        data object ChecksumInconsistency : Failure
        data class IoError(val cause: IOException) : Failure
    }
}

interface BackupArchiveWriter {
    suspend fun write(
        outputStream: OutputStream,
        plan: BackupPlan
    ): BackupArchiveWriteResult
}
