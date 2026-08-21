package com.venkoi.restaurantops.core.preferences.model

data class AppPreferences(
    val onboardingCompleted: Boolean,
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val appLocaleTag: String,
    val autoBackupEnabled: Boolean,
    val lastAutoBackupSuccessTimestamp: Long?,
    val lastAutoBackupAttemptTimestamp: Long?,
    val lastAutoBackupResult: String?,
    val menuManagementEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = AppPreferences(
            onboardingCompleted = false,
            themeMode = ThemeMode.SYSTEM,
            // Keep the intentionally designed brand palette as the out-of-box experience.
            // Users can still opt into wallpaper-derived Material colors in Settings.
            dynamicColorEnabled = false,
            appLocaleTag = "en-US",
            autoBackupEnabled = true, // Safety feature enabled by default
            lastAutoBackupSuccessTimestamp = null,
            lastAutoBackupAttemptTimestamp = null,
            lastAutoBackupResult = null,
            menuManagementEnabled = true
        )
    }
}
