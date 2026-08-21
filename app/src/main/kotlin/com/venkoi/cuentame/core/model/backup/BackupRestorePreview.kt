package com.venkoi.cuentame.core.model.backup

/**
 * Non-destructive preview of a backup archive to help the user identify and confirm the data.
 * Contains only validated, safe-to-display metadata.
 */
data class BackupRestorePreview(
    val restaurantName: String,
    val createdAt: Long?,
    val backupFormatVersion: Int,
    val databaseSchemaVersion: Int,
    val localeTag: String,
    val totalRecordCount: Long,
    val attachmentCount: Int,
    val totalAttachmentBytes: Long
)
