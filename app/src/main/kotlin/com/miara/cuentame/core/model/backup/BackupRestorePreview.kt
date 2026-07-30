package com.miara.cuentame.core.model.backup

/**
 * Non-destructive preview of a backup archive to help the user identify and confirm the data.
 */
data class BackupRestorePreview(
    val restaurantName: String,
    val createdAt: Long?,
    val backupFormatVersion: Int,
    val databaseSchemaVersion: Int,
    val localeTag: String,
    val tableCounts: Map<String, Int>,
    val attachmentCount: Int,
    val totalAttachmentBytes: Long
)
