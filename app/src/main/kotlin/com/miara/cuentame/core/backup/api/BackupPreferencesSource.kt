package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.model.backup.BackupPreferencesDto

interface BackupPreferencesSource {
    suspend fun loadPreferences(): BackupPreferencesDto
}
