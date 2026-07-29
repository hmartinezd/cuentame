package com.miara.cuentame.core.backup.api

import java.io.OutputStream

sealed interface BackupArchiveWriteResult {
    data object Success : BackupArchiveWriteResult
    sealed interface Failure : BackupArchiveWriteResult {
        data object AttachmentUnreadable : Failure
        data object AttachmentChanged : Failure
        data object LimitExceeded : Failure
        data class IoError(val cause: Exception) : Failure
    }
}

interface BackupArchiveWriter {
    suspend fun write(
        outputStream: OutputStream,
        plan: BackupPlan
    ): BackupArchiveWriteResult
}
