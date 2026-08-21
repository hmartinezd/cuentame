package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.api.BackupDocumentStore
import com.venkoi.restaurantops.core.backup.api.BackupDocumentUri
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupCleanupOutcome {
    data object Deleted : BackupCleanupOutcome
    data object Truncated : BackupCleanupOutcome
    data object Failed : BackupCleanupOutcome
}

@Singleton
class BackupCleanupCoordinator @Inject constructor(
    private val documentStore: BackupDocumentStore
) {
    suspend fun cleanup(uri: BackupDocumentUri): BackupCleanupOutcome {
        val deleted = runCatching {
            documentStore.delete(uri)
        }.getOrDefault(false)

        if (deleted) {
            return BackupCleanupOutcome.Deleted
        }

        val truncated = runCatching {
            documentStore.truncate(uri)
        }.getOrDefault(false)

        return if (truncated) {
            BackupCleanupOutcome.Truncated
        } else {
            BackupCleanupOutcome.Failed
        }
    }
}
