package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestorePreferencesApplier @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository
) {
    suspend fun captureRollback(): BackupPreferencesDto {
        val prefs = preferencesRepository.observePreferences().first()
        return BackupPreferencesDto(
            themeMode = prefs.themeMode.name,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            appLocaleTag = prefs.appLocaleTag
        )
    }

    suspend fun apply(dto: BackupPreferencesDto) {
        val themeMode = try {
            ThemeMode.valueOf(dto.themeMode)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        
        preferencesRepository.setThemeMode(themeMode)
        preferencesRepository.setDynamicColorEnabled(dto.dynamicColorEnabled)
        preferencesRepository.setAppLocaleTag(dto.appLocaleTag)
    }
}
