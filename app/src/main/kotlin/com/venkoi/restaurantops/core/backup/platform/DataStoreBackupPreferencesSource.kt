package com.venkoi.restaurantops.core.backup.platform

import com.venkoi.restaurantops.core.backup.api.BackupPreferencesSource
import com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreBackupPreferencesSource @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository
) : BackupPreferencesSource {

    override suspend fun loadPreferences(): BackupPreferencesDto {
        val prefs = preferencesRepository.observePreferences().first()
        return BackupPreferencesDto(
            themeMode = prefs.themeMode.name,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            appLocaleTag = prefs.appLocaleTag,
            menuManagementEnabled = prefs.menuManagementEnabled
        )
    }
}
