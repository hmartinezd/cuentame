package com.miara.cuentame.test

import android.content.Context
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
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

    fun resetAll() = runBlocking {
        database.clearAllTables()
        dataStoreOwner.clear()
        removeTestFiles()
    }

    private fun removeTestFiles() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.filesDir.listFiles()?.forEach { 
            if (it.name.contains("test") && it.name != dataStoreOwner.file.name) {
                it.deleteRecursively()
            }
        }
    }

    fun seedBaseline() = runBlocking {
        TestSeeder.seedBaseline(database)
        preferences.setOnboardingCompleted(true)
        preferences.setAppLocaleTag("en-US")
    }
}
