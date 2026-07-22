package com.miara.cuentame.feature.onboarding.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class OnboardingUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun onboarding_full_flow() {
        // Wait for resolve startup and initial load
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("onboarding_welcome_content", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Welcome Step
        composeTestRule.onNodeWithTag("onboarding_welcome_content", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).performClick()

        // Restaurant Step
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("onboarding_restaurant_name", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true).performTextInput("The Integrity Pass")
        // Close keyboard if needed
        composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true).performImeAction()
        
        composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

        // Wait for Areas Step
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("onboarding_areas_title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Add custom area
        composeTestRule.onNodeWithTag("onboarding_add_area_input", useUnmergedTree = true).performTextInput("Back Dock")
        composeTestRule.onNodeWithTag("onboarding_add_area_input", useUnmergedTree = true).performImeAction()
        composeTestRule.onNodeWithTag("onboarding_add_area_button", useUnmergedTree = true).performClick()
        
        assertThat(composeTestRule.onAllNodesWithText("Back Dock", ignoreCase = true).fetchSemanticsNodes()).isNotEmpty()
        
        composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).onFirst().performClick()

        // Wait for Categories Step
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("onboarding_categories_title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).onFirst().performClick()

        // Wait for Review Step
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Review Setup", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(composeTestRule.onAllNodesWithText("The Integrity Pass", ignoreCase = true, substring = true).fetchSemanticsNodes()).isNotEmpty()
        
        composeTestRule.onAllNodesWithTag("onboarding_finish_button", useUnmergedTree = true).onFirst().performClick()

        // Verify Home appears
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Dashboard", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Dashboard", ignoreCase = true).onFirst().assertIsDisplayed()
    }
}
