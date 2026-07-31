package com.miara.cuentame.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.test.TestStateManager
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

            composeTestRule.onNodeWithTag("nav_settings").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("settings_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        }
    }

    @Test
    fun openSettingsFromHomeCard_navigatesToSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("home_settings_card").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("settings_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        }
    }

    @Test
    fun settingsSubsections_navigateAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_settings").performClick()

            // Restaurant Profile
            composeTestRule.onNodeWithText("Restaurant Profile").performClick()
            composeTestRule.onNodeWithText("Restaurant Details").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // Areas
            composeTestRule.onNodeWithText("Inventory Areas").performClick()
            composeTestRule.onNodeWithText("Inventory Areas").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // Categories
            composeTestRule.onNodeWithText("Ingredient Categories").performClick()
            composeTestRule.onNodeWithText("Ingredient Categories").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // Suppliers
            composeTestRule.onNodeWithText("Suppliers").performClick()
            composeTestRule.onNodeWithText("Suppliers").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        }
    }
}
