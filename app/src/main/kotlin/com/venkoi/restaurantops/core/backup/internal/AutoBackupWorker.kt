package com.venkoi.restaurantops.core.backup.internal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val autoBackupRepository: AutoBackupRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            when (autoBackupRepository.performAutoBackup()) {
                AutoBackupOutcome.SUCCESS, AutoBackupOutcome.DISABLED -> Result.success()
                AutoBackupOutcome.TRANSIENT_FAILURE -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                AutoBackupOutcome.PERMANENT_FAILURE -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private companion object { const val MAX_ATTEMPTS = 3 }
}
