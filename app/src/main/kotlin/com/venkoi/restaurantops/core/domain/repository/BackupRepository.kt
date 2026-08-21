package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.model.backup.BackupResult
import com.venkoi.restaurantops.core.model.backup.BackupValidationResult
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    /**
     * Creates a full logical backup and writes it to the provided platform destination.
     * Emits the current phase of the operation.
     */
    fun createBackup(destinationUri: String): Flow<BackupOperationStatus>

    /**
     * Validates an existing backup archive.
     */
    suspend fun validateBackup(sourceUri: String): BackupValidationResult
}

sealed interface BackupOperationStatus {
    data object Creating : BackupOperationStatus
    data object Validating : BackupOperationStatus
    data class Success(val manifest: BackupManifest) : BackupOperationStatus
    data class Error(val result: BackupResult.Error) : BackupOperationStatus
}
