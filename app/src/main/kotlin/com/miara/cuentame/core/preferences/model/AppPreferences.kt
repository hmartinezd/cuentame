package com.miara.cuentame.core.preferences.model

data class AppPreferences(
    val onboardingCompleted: Boolean,
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val appLocaleTag: String,
    val autoBackupEnabled: Boolean,
    val lastAutoBackupSuccessTimestamp: Long?,
    val lastAutoBackupAttemptTimestamp: Long?,
    val lastAutoBackupResult: String?
) {
    companion object {
        val DEFAULT = AppPreferences(
            onboardingCompleted = false,
            themeMode = ThemeMode.SYSTEM,
            dynamicColorEnabled = true,
            appLocaleTag = "en-US",
            autoBackupEnabled = true, // Safety feature enabled by default
            lastAutoBackupSuccessTimestamp = null,
            lastAutoBackupAttemptTimestamp = null,
            lastAutoBackupResult = null
        )
    }
}
