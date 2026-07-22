package com.miara.cuentame.feature.onboarding.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
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
    fun onboarding_flow_validation() {
        composeTestRule.waitForIdle()

        // Welcome Step
        composeTestRule.onNodeWithTag("onboarding_welcome_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_setup_button").performClick()

        // Restaurant Step
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsNotEnabled()
        
        composeTestRule.onNodeWithTag("onboarding_restaurant_name").performTextInput("Test Restaurant")
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()

        // Areas Step
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()

        // Categories Step
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()

        // Review Step
        composeTestRule.onNodeWithTag("onboarding_finish_button").assertIsDisplayed()
    }
}
