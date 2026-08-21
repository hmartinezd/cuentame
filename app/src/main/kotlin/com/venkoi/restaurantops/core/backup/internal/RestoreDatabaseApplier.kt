package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.model.BackupSnapshotDto
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.model.backup.RestoreDatabaseRollbackSnapshot

interface RestoreDatabaseApplier {
    /**
     * Checks if current database contains any non-null attachment references.
     */
    suspend fun hasExistingAttachmentReferences(): Boolean

    /**
     * Captures a raw snapshot of the entire database for rollback, preserving raw paths.
     */
    suspend fun captureRollbackSnapshot(): RestoreDatabaseRollbackSnapshot

    /**
     * Replaces the entire database content with the provided snapshot.
     */
    suspend fun replaceWithBackup(snapshot: BackupSnapshotDto, manifest: BackupManifest)

    /**
     * Restores the database from a previously captured internal rollback snapshot.
     */
    suspend fun restoreRollback(rollback: RestoreDatabaseRollbackSnapshot)

    /**
     * Verifies that the current database state exactly matches the provided backup snapshot.
     */
    suspend fun verifyMatchesBackup(snapshot: BackupSnapshotDto, manifest: BackupManifest): Boolean

    /**
     * Verifies that the current database state exactly matches the provided rollback snapshot.
     */
    suspend fun verifyMatchesRollback(rollback: RestoreDatabaseRollbackSnapshot): Boolean
}
