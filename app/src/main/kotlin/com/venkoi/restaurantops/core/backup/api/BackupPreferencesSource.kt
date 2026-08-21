package com.venkoi.restaurantops.core.backup.api

import com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto

interface BackupPreferencesSource {
    suspend fun loadPreferences(): BackupPreferencesDto
}
