package com.miara.cuentame.core.backup.internal

import android.content.Context
import com.miara.cuentame.core.backup.AndroidBackupRepository
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class AutoBackupOutcome { SUCCESS, DISABLED, TRANSIENT_FAILURE, PERMANENT_FAILURE }

@Singleton
class AutoBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: AndroidBackupRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider
) {
    private val autoBackupDir = File(context.filesDir, "backups")

    suspend fun performAutoBackup(): AutoBackupOutcome {
        if (!preferencesRepository.observePreferences().first().autoBackupEnabled) {
            return AutoBackupOutcome.DISABLED
        }

        val timestamp = timeProvider.now()
        if (!autoBackupDir.exists() && !autoBackupDir.mkdirs()) {
            recordFailure(timestamp.toEpochMilli(), "DIRECTORY_FAILURE")
            return AutoBackupOutcome.PERMANENT_FAILURE
        }

        val filename = "cuentame-auto-${DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")).format(timestamp)}.zip"
        val tempFile = File(autoBackupDir, "$filename.tmp")
        val finalFile = File(autoBackupDir, filename)

        return try {
            when (val status = backupRepository.createBackup(tempFile.absolutePath)
                .first { it is BackupOperationStatus.Success || it is BackupOperationStatus.Error }) {
                is BackupOperationStatus.Success -> {
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.delete()
                        recordFailure(timestamp.toEpochMilli(), "RENAME_FAILURE")
                        AutoBackupOutcome.TRANSIENT_FAILURE
                    } else {
                        rotateBackups()
                        preferencesRepository.updateAutoBackupStatus(
                            successTimestamp = timestamp.toEpochMilli(),
                            attemptTimestamp = timestamp.toEpochMilli(),
                            result = "SUCCESS"
                        )
                        AutoBackupOutcome.SUCCESS
                    }
                }
                is BackupOperationStatus.Error -> {
                    tempFile.delete()
                    val category = safeCategory(status.result)
                    recordFailure(timestamp.toEpochMilli(), category)
                    if (status.result.isTransient()) AutoBackupOutcome.TRANSIENT_FAILURE else AutoBackupOutcome.PERMANENT_FAILURE
                }
                else -> error("Terminal backup status expected")
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: IOException) {
            tempFile.delete()
            recordFailure(timestamp.toEpochMilli(), "IO_FAILURE")
            AutoBackupOutcome.TRANSIENT_FAILURE
        } catch (e: Exception) {
            tempFile.delete()
            recordFailure(timestamp.toEpochMilli(), "INTERNAL_FAILURE")
            AutoBackupOutcome.PERMANENT_FAILURE
        }
    }

    private suspend fun recordFailure(attemptTimestamp: Long, category: String) {
        // A null success timestamp intentionally preserves the last known-good value in DataStore.
        preferencesRepository.updateAutoBackupStatus(null, attemptTimestamp, category)
    }

    private fun safeCategory(error: BackupResult.Error): String = when (error) {
        BackupResult.Error.InsufficientStorage -> "OUT_OF_STORAGE"
        BackupResult.Error.SystemIOFailure, BackupResult.Error.DestinationUnavailable -> "IO_FAILURE"
        is BackupResult.Error.ArchiveValidationFailure -> "ARCHIVE_VALIDATION_FAILURE"
        else -> "STRUCTURAL_FAILURE"
    }

    private fun BackupResult.Error.isTransient(): Boolean =
        this == BackupResult.Error.SystemIOFailure || this == BackupResult.Error.DestinationUnavailable

    private fun rotateBackups() {
        val backups = autoBackupDir.listFiles { file ->
            file.isFile && file.name.startsWith("cuentame-auto-") && file.name.endsWith(".zip")
        }?.sortedByDescending { it.name } ?: return
        backups.drop(7).forEach { it.delete() }
    }
}
