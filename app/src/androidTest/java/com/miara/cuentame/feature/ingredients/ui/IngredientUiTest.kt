package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
class IngredientUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun ingredient_list_to_create_flow() {
        // Wait for resolve startup
        composeTestRule.waitForIdle()

        // If in onboarding, complete it quickly
        val setupAction = composeTestRule.activity.getString(R.string.onboarding_setup_action)
        val setupExists = composeTestRule.onAllNodesWithText(setupAction).fetchSemanticsNodes().isNotEmpty()
        
        if (setupExists) {
            composeTestRule.onNodeWithText(setupAction).performClick()
            composeTestRule.onNodeWithTag("onboarding_restaurant_name").performTextInput("Test Rest")
            composeTestRule.onNodeWithTag("onboarding_next_button").performClick() // to areas
            composeTestRule.onAllNodesWithTag("onboarding_next_button")[0].performClick() // to categories
            composeTestRule.onAllNodesWithTag("onboarding_next_button")[0].performClick() // to review
            composeTestRule.onAllNodesWithTag("onboarding_finish_button")[0].performClick()
            composeTestRule.waitForIdle()
        }

        // Navigate to Inventory
        val navInventory = composeTestRule.activity.getString(R.string.nav_inventory)
        composeTestRule.onNodeWithText(navInventory).performClick()

        // Click Add
        val addIngredient = composeTestRule.activity.getString(R.string.add_ingredient)
        composeTestRule.onNodeWithContentDescription(addIngredient).performClick()

        // Fill form
        val nameLabel = composeTestRule.activity.getString(R.string.ingredient_name)
        composeTestRule.onNodeWithText(nameLabel).performTextInput("Chicken Breast")
        
        // Save
        val saveLabel = composeTestRule.activity.getString(R.string.action_save)
        composeTestRule.onNodeWithText(saveLabel).performClick()
        
        // Verify detail appears
        composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
    }
}
