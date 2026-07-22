package com.miara.cuentame.core.preferences.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

class DataStoreAppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) : AppPreferencesRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val APP_LOCALE_TAG = stringPreferencesKey("app_locale_tag")
        val ONBOARDING_DRAFT = stringPreferencesKey("onboarding_draft")
    }

    override fun observePreferences(): Flow<AppPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppPreferences(
                onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
                themeMode = try {
                    ThemeMode.valueOf(preferences[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                } catch (e: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                },
                dynamicColorEnabled = preferences[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
                appLocaleTag = preferences[Keys.APP_LOCALE_TAG] ?: "en-US"
            )
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    override suspend fun setAppLocaleTag(localeTag: String) {
        dataStore.edit { it[Keys.APP_LOCALE_TAG] = localeTag }
    }

    override fun observeOnboardingDraft(): Flow<OnboardingDraft?> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[Keys.ONBOARDING_DRAFT] ?: return@map null
            try {
                val draft = json.decodeFromString<OnboardingDraft>(jsonString)
                if (draft.formatVersion != 2) {
                    Log.w("DataStorePrefs", "Unsupported draft version: ${draft.formatVersion}. Expected 2. Clearing.")
                    scope.launch { clearOnboardingDraft() }
                    null
                } else {
                    draft
                }
            } catch (e: Exception) {
                Log.e("DataStorePrefs", "Failed to decode onboarding draft, clearing it", e)
                scope.launch { clearOnboardingDraft() }
                null
            }
        }

    override suspend fun saveOnboardingDraft(draft: OnboardingDraft) {
        try {
            val jsonString = json.encodeToString(draft)
            dataStore.edit { it[Keys.ONBOARDING_DRAFT] = jsonString }
        } catch (e: Exception) {
            Log.e("DataStorePrefs", "Failed to encode/save onboarding draft", e)
            throw ValidationError.OnboardingDraftSaveFailed
        }
    }

    override suspend fun clearOnboardingDraft() {
        dataStore.edit { it.remove(Keys.ONBOARDING_DRAFT) }
    }
}
