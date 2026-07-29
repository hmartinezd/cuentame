package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.api.BackupDocumentStore
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCleanupCoordinator @Inject constructor(
    private val documentStore: BackupDocumentStore
) {
    // For testing purposes
    var lastCleanupOutcome: CleanupOutcome? = null
        private set

    sealed interface CleanupOutcome {
        data object Deleted : CleanupOutcome
        data object Truncated : CleanupOutcome
        data object Failed : CleanupOutcome
    }

    suspend fun cleanup(uri: BackupDocumentUri) {
        try {
            if (documentStore.delete(uri)) {
                lastCleanupOutcome = CleanupOutcome.Deleted
                return
            }
            if (documentStore.truncate(uri)) {
                lastCleanupOutcome = CleanupOutcome.Truncated
                return
            }
            lastCleanupOutcome = CleanupOutcome.Failed
        } catch (_: Exception) {
            lastCleanupOutcome = CleanupOutcome.Failed
        }
    }
}
