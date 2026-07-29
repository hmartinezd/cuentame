package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.BackupDocumentStore
import com.miara.cuentame.core.backup.api.BackupDocumentUri
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
        return try {
            if (documentStore.delete(uri)) {
                BackupCleanupOutcome.Deleted
            } else if (documentStore.truncate(uri)) {
                BackupCleanupOutcome.Truncated
            } else {
                BackupCleanupOutcome.Failed
            }
        } catch (_: Exception) {
            BackupCleanupOutcome.Failed
        }
    }
}
