package com.miara.cuentame.core.preferences.repository

import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    suspend fun setAppLocaleTag(localeTag: String)

    fun observeOnboardingDraft(): Flow<OnboardingDraft?>
    suspend fun saveOnboardingDraft(draft: OnboardingDraft)
    suspend fun clearOnboardingDraft()
}
