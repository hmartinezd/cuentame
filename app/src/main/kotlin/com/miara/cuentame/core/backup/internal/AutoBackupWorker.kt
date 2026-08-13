package com.miara.cuentame.core.backup.internal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val autoBackupRepository: AutoBackupRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            autoBackupRepository.performAutoBackup()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
