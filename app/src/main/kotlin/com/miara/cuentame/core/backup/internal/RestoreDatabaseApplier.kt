package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.model.BackupSnapshotDto

interface RestoreDatabaseApplier {
    /**
     * Captures a raw snapshot of the entire database for rollback.
     */
    suspend fun captureRollbackSnapshot(): BackupSnapshotDto

    /**
     * Replaces the entire database content with the provided snapshot.
     */
    suspend fun replaceWith(snapshot: BackupSnapshotDto)
}
