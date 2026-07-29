package com.miara.cuentame.feature.onboarding.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OnboardingUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun setup() {
        hiltRule.inject()
        testStateManager.resetAll()
    }

    @After
    fun tearDown() {
        testStateManager.resetAll()
    }

    @Test
    fun onboarding_start_navigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()

            // Welcome Step visibility
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_welcome_content", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).assertIsDisplayed()
        }
    }
}
