package com.miara.cuentame

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.test.TestStateManager
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
        testStateManager.resetAll()
    }

    @After
    fun tearDown() {
        testStateManager.resetAll()
    }

    @Test
    fun app_startsOnOnboarding_whenNoRestaurant() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        }
    }

    @Test
    fun app_startsOnHome_whenRestaurantExists() {
        testStateManager.seedBaseline()
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun navigateToSettingsAndBack() {
        testStateManager.seedBaseline()
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_settings_button").performClick()
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("settings_back_button").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }
}
