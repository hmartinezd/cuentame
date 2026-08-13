package com.miara.cuentame.core.common

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceInfoProvider {
    val manufacturer: String
    val model: String
    val sdkInt: Int
}

@Singleton
class AndroidDeviceInfoProvider @Inject constructor() : DeviceInfoProvider {
    override val manufacturer: String get() = Build.MANUFACTURER ?: "Unknown"
    override val model: String get() = Build.MODEL ?: "Unknown"
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
}
