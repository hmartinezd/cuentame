package com.miara.cuentame.core.backup.internal

import android.content.Context
import com.miara.cuentame.core.backup.AndroidBackupRepository
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: AndroidBackupRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider
) {
    private val autoBackupDir = File(context.filesDir, "backups")

    suspend fun performAutoBackup() {
        if (!autoBackupDir.exists() && !autoBackupDir.mkdirs()) {
            preferencesRepository.updateAutoBackupStatus(
                successTimestamp = null,
                attemptTimestamp = timeProvider.now().toEpochMilli(),
                result = "DIR_CREATION_FAILED"
            )
            return
        }

        val timestamp = timeProvider.now()
        // cuentame-auto-2026-08-12T213000Z.zip
        val filename = "cuentame-auto-${DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")).format(timestamp)}.zip"
        val tempFile = File(autoBackupDir, "$filename.tmp")
        val finalFile = File(autoBackupDir, filename)

        try {
            // Trigger backup using existing logic
            val status = backupRepository.createBackup(tempFile.absolutePath)
                .first { it is BackupOperationStatus.Success || it is BackupOperationStatus.Error }
            
            if (status is BackupOperationStatus.Success) {
                if (tempFile.renameTo(finalFile)) {
                    rotateBackups()
                    preferencesRepository.updateAutoBackupStatus(
                        successTimestamp = timestamp.toEpochMilli(),
                        attemptTimestamp = timestamp.toEpochMilli(),
                        result = "SUCCESS"
                    )
                } else {
                    tempFile.delete()
                    preferencesRepository.updateAutoBackupStatus(
                        successTimestamp = null,
                        attemptTimestamp = timestamp.toEpochMilli(),
                        result = "RENAME_FAILED"
                    )
                }
            } else if (status is BackupOperationStatus.Error) {
                tempFile.delete()
                preferencesRepository.updateAutoBackupStatus(
                    successTimestamp = null,
                    attemptTimestamp = timestamp.toEpochMilli(),
                    result = status.result.toString().take(50)
                )
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            preferencesRepository.updateAutoBackupStatus(
                successTimestamp = null,
                attemptTimestamp = timestamp.toEpochMilli(),
                result = e.message?.take(50) ?: e.javaClass.simpleName
            )
        }
    }

    private fun rotateBackups() {
        val backups = autoBackupDir.listFiles { file ->
            file.isFile && file.name.startsWith("cuentame-auto-") && file.name.endsWith(".zip")
        }?.sortedByDescending { it.name } ?: return

        if (backups.size > 7) {
            // Keep the most recent 7 successful backups
            backups.drop(7).forEach { it.delete() }
        }
    }
}
