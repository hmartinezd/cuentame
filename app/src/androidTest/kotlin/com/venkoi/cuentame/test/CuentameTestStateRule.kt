package com.venkoi.cuentame.test

import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import javax.inject.Inject

class CuentameTestStateRule @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val preferences: AppPreferencesRepository
) : TestWatcher() {

    override fun starting(description: Description?) {
        resetStorage()
    }

    override fun finished(description: Description?) {
        resetStorage()
    }

    private fun resetStorage() = runBlocking {
        database.clearAllTables()
        preferences.clearAll()
    }

    fun seedRestaurant() = runBlocking {
        TestSeeder.seedBaseline(database)
        preferences.setOnboardingCompleted(true)
        preferences.setAppLocaleTag("en-US")
    }
}
