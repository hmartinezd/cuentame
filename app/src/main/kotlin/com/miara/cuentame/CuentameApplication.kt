package com.miara.cuentame

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.miara.cuentame.core.backup.internal.AutoBackupScheduler
import com.miara.cuentame.core.backup.internal.RecoveryBootstrapper
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class CuentameApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var recoveryBootstrapper: RecoveryBootstrapper

    @Inject
    lateinit var autoBackupScheduler: AutoBackupScheduler

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        recoveryBootstrapper.bootstrap()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (preferencesRepository.observePreferences().first().autoBackupEnabled) {
                autoBackupScheduler.scheduleDailyBackup()
            } else {
                autoBackupScheduler.cancelDailyBackup()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
