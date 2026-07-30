package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto

/**
 * Validated in-memory representation of a backup archive suitable for application.
 * Does not retain large attachment payloads.
 */
data class InspectedBackupArchive(
    val snapshot: BackupSnapshotDto,
    val preferences: BackupPreferencesDto,
    val manifest: BackupManifest,
    val attachmentSummaries: List<InspectedBackupAttachment>,
    val source: BackupDocumentUri
)

data class InspectedBackupAttachment(
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val checksumSha256: String
)
