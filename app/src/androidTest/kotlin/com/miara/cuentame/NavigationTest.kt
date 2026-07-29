package com.miara.cuentame

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
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun app_startsInHome() {
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }

    @Test
    fun navigateToSettingsAndBack() {
        composeTestRule.onNodeWithTag("home_settings_button").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertExists()
        
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }

    @Test
    fun navigateToIngredientsAndBack() {
        composeTestRule.onNodeWithTag("nav_ingredients").performClick()
        composeTestRule.onNodeWithTag("ingredient_list_screen").assertExists()
        
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }
}
