package com.venkoi.cuentame.core.backup.api

import com.venkoi.cuentame.core.model.backup.BackupPreferencesDto

interface BackupPreferencesSource {
    suspend fun loadPreferences(): BackupPreferencesDto
}
