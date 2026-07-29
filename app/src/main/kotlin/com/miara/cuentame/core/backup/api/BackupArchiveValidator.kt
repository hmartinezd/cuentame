package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.model.backup.BackupValidationResult
import java.io.InputStream

data class AttachmentReferenceKey(
    val attachmentId: String,
    val recordType: String,
    val recordId: String
)

interface BackupArchiveValidator {
    fun validate(inputStream: InputStream): BackupValidationResult
}
