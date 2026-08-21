package com.venkoi.restaurantops.core.backup.platform

import com.venkoi.restaurantops.core.backup.api.BackupDocumentOpenException
import com.venkoi.restaurantops.core.backup.api.BackupStorageFailure
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface BackupStorageErrorClassifier {
    fun classify(throwable: Throwable): BackupStorageFailure
}

@Singleton
class DefaultBackupStorageErrorClassifier @Inject constructor() : BackupStorageErrorClassifier {

    override fun classify(throwable: Throwable): BackupStorageFailure {
        val chain = generateSequence(throwable) { it.cause }.toList()

        // 1. SecurityException anywhere -> PermissionDenied
        if (chain.any { it is SecurityException }) {
            return BackupStorageFailure.PermissionDenied
        }

        // 2. ENOSPC anywhere -> InsufficientSpace
        if (chain.any { isInsufficientSpace(it) }) {
            return BackupStorageFailure.InsufficientSpace
        }

        // 3. BackupDocumentOpenException -> DestinationUnavailable
        if (chain.any { it is BackupDocumentOpenException }) {
            return BackupStorageFailure.DestinationUnavailable
        }

        // 4. IOException -> GenericIo
        if (chain.any { it is IOException }) {
            return BackupStorageFailure.GenericIo
        }

        return BackupStorageFailure.GenericIo
    }

    private fun isInsufficientSpace(t: Throwable): Boolean {
        if (t is IOException) {
            val message = t.message ?: return false
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left on device", ignoreCase = true)) {
                return true
            }
        }
        
        // ErrnoException errno 28 = ENOSPC
        if (t.javaClass.simpleName == "ErrnoException") {
            try {
                val errnoField = t.javaClass.getField("errno")
                if (errnoField.get(t) == 28) return true
            } catch (_: Exception) {}
        }
        
        return false
    }
}
