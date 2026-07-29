package com.miara.cuentame.core.backup.platform

import java.io.IOException

interface BackupStorageErrorClassifier {
    fun isInsufficientStorage(throwable: Throwable): Boolean
}

class DefaultBackupStorageErrorClassifier : BackupStorageErrorClassifier {

    override fun isInsufficientStorage(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.any { cause ->
            when {
                cause is IOException && (
                    cause.message?.contains("ENOSPC", ignoreCase = true) == true ||
                    cause.message?.contains("No space left on device", ignoreCase = true) == true
                ) -> true
                cause.javaClass.simpleName == "ErrnoException" -> {
                    try {
                        val errnoField = cause.javaClass.getField("errno")
                        errnoField.getInt(cause) == 28
                    } catch (_: Exception) { false }
                }
                else -> false
            }
        }
    }
}
