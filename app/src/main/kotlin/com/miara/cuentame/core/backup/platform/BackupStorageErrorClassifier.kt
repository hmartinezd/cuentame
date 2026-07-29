package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.BackupStorageFailure
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface BackupStorageErrorClassifier {
    fun classify(throwable: Throwable): BackupStorageFailure
}

@Singleton
class DefaultBackupStorageErrorClassifier @Inject constructor() : BackupStorageErrorClassifier {

    override fun classify(throwable: Throwable): BackupStorageFailure {
        return generateSequence(throwable) { it.cause }.map { cause ->
            when {
                cause is SecurityException -> BackupStorageFailure.PermissionDenied
                cause is IOException && isInsufficientSpace(cause) -> BackupStorageFailure.InsufficientSpace
                cause is IOException -> BackupStorageFailure.GenericIo
                cause.javaClass.simpleName == "ErrnoException" && isEnospc(cause) -> BackupStorageFailure.InsufficientSpace
                else -> null
            }
        }.filterNotNull().firstOrNull() ?: BackupStorageFailure.GenericIo
    }

    private fun isInsufficientSpace(e: IOException): Boolean {
        val message = e.message ?: return false
        return message.contains("ENOSPC", ignoreCase = true) ||
               message.contains("No space left on device", ignoreCase = true)
    }

    private fun isEnospc(errnoException: Throwable): Boolean {
        return try {
            val errnoField = errnoException.javaClass.getField("errno")
            errnoField.getInt(errnoException) == 28 // ENOSPC
        } catch (_: Exception) {
            false
        }
    }
}
