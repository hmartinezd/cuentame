package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto

data class BackupAttachmentSourceBinding(
    val attachmentId: String,
    val sourceUri: AttachmentSourceUri
)

data class BackupSnapshotResult(
    val dto: BackupSnapshotDto,
    val attachmentBindings: List<BackupAttachmentSourceBinding>
)

interface BackupSnapshotSource {
    suspend fun loadSnapshot(restaurantId: String): BackupSnapshotResult
}
