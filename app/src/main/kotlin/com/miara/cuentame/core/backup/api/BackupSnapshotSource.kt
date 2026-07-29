package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto

interface BackupSnapshotSource {
    suspend fun loadSnapshot(restaurantId: String): BackupSnapshotDto
}
