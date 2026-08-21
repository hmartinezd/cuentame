package com.venkoi.restaurantops.test

import android.content.Context
import com.venkoi.restaurantops.core.backup.api.RestoreStartupState
import com.venkoi.restaurantops.core.backup.internal.RestoreOperationGate
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
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
        // Re-seed system units because clearAllTables() wiped them, and addCallback only runs once
        database.openHelper.writableDatabase.let { db ->
            com.venkoi.restaurantops.core.database.seed.SystemUnitSeeder.seed(db)
        }
        // For tests, default to Ready so the app doesn't hang in LoadingContent
        restoreGate.updateRecoveryState(RestoreStartupState.Ready)
    }

    private fun removeTestFiles() {
        // Targeted recursive cleanup of known test artifact paths
        val filesDirPaths = listOf("attachments", "backup_restore", "construction")
        filesDirPaths.forEach { path ->
            File(context.filesDir, path).deleteRecursively()
        }

        listOf(context.filesDir, context.cacheDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("integration_test_") || 
                    name.startsWith("backup_integration_") ||
                    name.contains("test_attachment") ||
                    name.contains("cuentame_test_backup")) {
                    file.deleteRecursively()
                }
            }
        }
    }

    suspend fun seedBaseline() {
        TestSeeder.seedBaseline(database)
        preferences.setOnboardingCompleted(true)
        preferences.setAppLocaleTag("en-US")
        restoreGate.updateRecoveryState(RestoreStartupState.Ready)
    }
}
