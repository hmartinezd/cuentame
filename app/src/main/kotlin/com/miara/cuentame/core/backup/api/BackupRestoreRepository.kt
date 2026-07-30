package com.miara.cuentame.core.backup.api

/**
 * Orchestrates backup restore operations, including inspection and eventual application.
 */
interface BackupRestoreRepository {
    /**
     * Inspects a backup archive at the provided URI.
     */
    suspend fun inspect(source: BackupDocumentUri): BackupArchiveInspectionResult
}
