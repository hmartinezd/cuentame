package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.backup.BackupValidationResult

interface BackupRepository {
    /**
     * Creates a full logical backup and writes it to the provided platform destination.
     * The destination is expected to be a writeable location (e.g., SAF Uri).
     */
    suspend fun createBackup(destinationUri: String): BackupResult

    /**
     * Validates an existing backup archive.
     */
    suspend fun validateBackup(sourceUri: String): BackupValidationResult
}
