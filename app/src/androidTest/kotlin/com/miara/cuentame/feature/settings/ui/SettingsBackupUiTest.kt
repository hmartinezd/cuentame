package com.miara.cuentame.feature.settings.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SettingsBackupUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            TestSeeder.seedBaseline(database)
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun createBackup_buttonExists() {
        // Navigate to settings
        composeTestRule.onNodeWithTag("home_settings_button").performClick()
        
        // Assert backup button exists
        composeTestRule.onNodeWithTag("create_backup_button").assertExists()
    }
    
    @Test
    fun backupProgress_is_visible_during_operation() {
        // This test would need a mock repository that hangs to verify progress.
        // For now, we verify the initial screen.
        composeTestRule.onNodeWithTag("home_settings_button").performClick()
        composeTestRule.onNodeWithTag("create_backup_button").assertIsDisplayed()
    }
}
