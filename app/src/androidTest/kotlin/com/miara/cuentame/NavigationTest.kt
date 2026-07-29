package com.miara.cuentame

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.test.TestStateManager
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking { testStateManager.resetAll() }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun app_startsOnOnboarding_whenNoRestaurant() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("onboarding_screen_root").assertIsDisplayed()
        }
    }

    @Test
    fun app_startsOnHome_whenRestaurantExists() {
        runBlocking { testStateManager.seedBaseline() }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun navigateToSettingsAndBack() {
        runBlocking { testStateManager.seedBaseline() }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("settings_back_button").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }
}
