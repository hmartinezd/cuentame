package com.miara.cuentame.test

import android.content.Context
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestStateManager @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val preferences: AppPreferencesRepository,
    private val dataStoreOwner: TestDataStoreOwner,
    private val restoreGate: RestoreOperationGate,
    @ApplicationContext private val context: Context
) {

    suspend fun resetAll() {
        database.clearAllTables()
        dataStoreOwner.clear()
        removeTestFiles()
        restoreGate.updateRecoveryState(RestoreStartupState.NotStarted)
    }

    private fun removeTestFiles() {
        // ... (unchanged)
    }

    suspend fun seedBaseline() {
        TestSeeder.seedBaseline(database)
        preferences.setOnboardingCompleted(true)
        preferences.setAppLocaleTag("en-US")
        restoreGate.updateRecoveryState(RestoreStartupState.Ready)
    }
}
