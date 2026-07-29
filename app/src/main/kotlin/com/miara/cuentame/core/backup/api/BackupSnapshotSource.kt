package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto

data class BackupSnapshotResult(
    val dto: BackupSnapshotDto,
    val attachmentUris: Map<String, AttachmentSourceUri> // ID -> URI
)

interface BackupSnapshotSource {
    suspend fun loadSnapshot(restaurantId: String): BackupSnapshotResult
}
