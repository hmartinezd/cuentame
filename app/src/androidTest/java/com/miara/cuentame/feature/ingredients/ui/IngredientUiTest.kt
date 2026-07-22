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
    fun complete_ingredient_creation_flow() {
        composeTestRule.waitForIdle()

        // If in onboarding, complete it quickly
        val setupAction = composeTestRule.activity.getString(R.string.onboarding_setup_action)
        val setupExists = composeTestRule.onAllNodesWithText(setupAction).fetchSemanticsNodes().isNotEmpty()
        
        if (setupExists) {
            composeTestRule.onNodeWithText(setupAction).performClick()
            composeTestRule.onNodeWithTag("onboarding_restaurant_name").performTextInput("Test Rest")
            composeTestRule.onNodeWithTag("onboarding_next_button").performClick() // to areas
            composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true)[0].performClick() // to categories
            composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true)[0].performClick() // to review
            composeTestRule.onAllNodesWithTag("onboarding_finish_button", useUnmergedTree = true)[0].performClick()
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
        
        // Select Dimension (Mass)
        val dimLabel = composeTestRule.activity.getString(R.string.measurement_dimension)
        composeTestRule.onNodeWithText(dimLabel).performClick()
        composeTestRule.onNodeWithText("Mass").performClick() // Use literal for simplicity in test if localized fails

        // Select Base Unit (Pound)
        val baseUnitLabel = composeTestRule.activity.getString(R.string.base_unit)
        composeTestRule.onNodeWithText(baseUnitLabel).performClick()
        composeTestRule.onNodeWithText("Pound (lb)").performClick()

        // Add Case Package
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.package_option)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.package_name)).performTextInput("Case")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.contains_quantity)).performTextInput("40")
        composeTestRule.onNodeWithTag("package_dialog_confirm").performClick()

        // Save Ingredient
        composeTestRule.onNodeWithTag("ingredient_form_save").performClick()
        
        // Wait for detail appears
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
    }
}
