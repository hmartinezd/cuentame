package com.miara.cuentame.feature.settings.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.miara.cuentame.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SettingsBackupUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun createBackup_buttonExists() {
        // Navigate to settings
        composeTestRule.onNodeWithTag("home_settings_button").performClick()
        
        // Assert backup button exists
        composeTestRule.onNodeWithTag("create_backup_button").assertExists()
    }
}
