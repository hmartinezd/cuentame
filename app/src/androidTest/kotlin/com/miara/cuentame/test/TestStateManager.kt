package com.miara.cuentame.test

import android.content.Context
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
    @ApplicationContext private val context: Context
) {

    suspend fun resetAll() {
        database.clearAllTables()
        dataStoreOwner.clear()
        removeTestFiles()
    }

    private fun removeTestFiles() {
        val targetPatterns = listOf("integration_test", "cuentame_test_backup", "test_attachment", "test_document")
        
        fun cleanDir(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (targetPatterns.any { pattern -> file.name.contains(pattern) }) {
                    file.deleteRecursively()
                }
            }
        }

        cleanDir(context.cacheDir)
        cleanDir(context.filesDir)
    }

    suspend fun seedBaseline() {
        TestSeeder.seedBaseline(database)
        preferences.setOnboardingCompleted(true)
        preferences.setAppLocaleTag("en-US")
    }
}
