package com.venkoi.cuentame.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SettingsNavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.seedBaseline()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun openSettingsFromTopBar_navigatesToSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify Settings gear on Home
            composeTestRule.onNodeWithTag("nav_settings").assertIsDisplayed().performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("settings_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            
            // Verify Back button on Settings root
            composeTestRule.onNodeWithTag("settings_back").assertIsDisplayed().performClick()
            
            // Verify back to Home
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun homeSettingsCard_isRemoved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("home_settings_card").assertDoesNotExist()
        }
    }

    @Test
    fun primaryNavigation_visibilityRules() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Home (Top-level)
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            // On compact, bottom bar should have nav_home, nav_inventory, etc.
            // On expanded, rail should have them. We check for the tags.
            composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_inventory").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_settings").assertIsDisplayed()

            // Settings Root
            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
            
            // Primary nav should be hidden
            composeTestRule.onNodeWithTag("nav_home").assertDoesNotExist()
            composeTestRule.onNodeWithTag("nav_inventory").assertDoesNotExist()
            // Settings gear should be hidden in Settings root
            composeTestRule.onNodeWithTag("nav_settings").assertDoesNotExist()
        }
    }

    @Test
    fun settingsSubsections_navigateAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_settings").performClick()

            // Restaurant Profile
            composeTestRule.onNodeWithTag("settings_item_RESTAURANT").performClick()
            // Secondary screens should not show bottom bar/rail
            composeTestRule.onNodeWithTag("nav_home").assertDoesNotExist()
            // Subsection top bar should have back
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // Areas
            composeTestRule.onNode(hasText("Inventory Areas") and hasClickAction()).performClick()
            composeTestRule.onNodeWithTag("nav_home").assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        }
    }

}
