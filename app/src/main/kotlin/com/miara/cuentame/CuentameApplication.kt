package com.miara.cuentame

import android.app.Application
import com.miara.cuentame.core.backup.internal.RecoveryBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CuentameApplication : Application() {
    @Inject
    lateinit var recoveryBootstrapper: RecoveryBootstrapper

    override fun onCreate() {
        super.onCreate()
        recoveryBootstrapper.bootstrap()
    }
}
