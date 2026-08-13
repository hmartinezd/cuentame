package com.miara.cuentame

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.miara.cuentame.core.backup.internal.AutoBackupScheduler
import com.miara.cuentame.core.backup.internal.RecoveryBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CuentameApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var recoveryBootstrapper: RecoveryBootstrapper

    @Inject
    lateinit var autoBackupScheduler: AutoBackupScheduler

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        recoveryBootstrapper.bootstrap()
        autoBackupScheduler.scheduleDailyBackup()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
