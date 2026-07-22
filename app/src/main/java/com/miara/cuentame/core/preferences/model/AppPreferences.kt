package com.miara.cuentame.core.preferences.model

data class AppPreferences(
    val onboardingCompleted: Boolean,
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val appLocaleTag: String
) {
    companion object {
        val DEFAULT = AppPreferences(
            onboardingCompleted = false,
            themeMode = ThemeMode.SYSTEM,
            dynamicColorEnabled = true,
            appLocaleTag = "en-US"
        )
    }
}
