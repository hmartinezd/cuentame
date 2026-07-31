package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.locale.SupportedAppLocale
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
        return readCurrent()
    }

    fun validate(dto: BackupPreferencesDto): Boolean {
        return try {
            ThemeMode.valueOf(dto.themeMode)
            SupportedAppLocale.fromLanguageTag(dto.appLocaleTag) != null
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun apply(dto: BackupPreferencesDto) {
        if (!validate(dto)) {
            throw IllegalArgumentException("Invalid preferences DTO: $dto")
        }
        
        val themeMode = ThemeMode.valueOf(dto.themeMode)
        
        preferencesRepository.setThemeMode(themeMode)
        preferencesRepository.setDynamicColorEnabled(dto.dynamicColorEnabled)
        preferencesRepository.setAppLocaleTag(dto.appLocaleTag)
    }

    suspend fun readCurrent(): BackupPreferencesDto {
        val prefs = preferencesRepository.observePreferences().first()
        return BackupPreferencesDto(
            themeMode = prefs.themeMode.name,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            appLocaleTag = prefs.appLocaleTag
        )
    }

    suspend fun verifyMatches(expected: BackupPreferencesDto): Boolean {
        return readCurrent() == expected
    }
}
