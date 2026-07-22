package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
    fun complete_ingredient_e2e_flow() {
        composeTestRule.waitForIdle()

        // 1. Complete onboarding if needed
        ensureOnboarded()

        // 2. Navigate to Inventory
        val navInventory = composeTestRule.activity.getString(R.string.nav_inventory)
        composeTestRule.onAllNodesWithText(navInventory).onFirst().performClick()
        composeTestRule.waitForIdle()

        // 3. Create Chicken Breast
        val addLabel = composeTestRule.activity.getString(R.string.add_ingredient)
        composeTestRule.onNodeWithContentDescription(addLabel).performClick()
        composeTestRule.waitForIdle()
        
        val nameLabel = composeTestRule.activity.getString(R.string.ingredient_name)
        composeTestRule.onNodeWithText(nameLabel).performTextInput("Chicken Breast")
        composeTestRule.waitForIdle()
        
        // Select Dimension: Mass
        composeTestRule.onNodeWithTag("dimension_selector").performClick()
        composeTestRule.waitForIdle()
        val massLabel = composeTestRule.activity.getString(R.string.dim_mass)
        composeTestRule.onAllNodesWithText(massLabel).onFirst().performClick()
        composeTestRule.waitForIdle()

        // Select Base Unit: Pound
        composeTestRule.onNodeWithTag("base_unit_selector").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Pound", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Pound", substring = true).onFirst().performClick()
        composeTestRule.waitForIdle()

        // Add Ounce Standard Unit
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.standard_unit)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Ounce", substring = true).onFirst().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("standard_unit_dialog_confirm").performClick()
        composeTestRule.waitForIdle()

        // Add Case Package
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.package_option)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.package_name)).performTextInput("Case")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.contains_quantity)).performTextInput("40")
        composeTestRule.onNodeWithTag("package_dialog_confirm").performClick()
        composeTestRule.waitForIdle()

        // Save Ingredient
        composeTestRule.onNodeWithTag("ingredient_form_save").performClick()
        composeTestRule.waitForIdle()
        
        // 4. Verify Detail
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Chicken Breast", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Chicken Breast", substring = true).onFirst().assertIsDisplayed()

        // 5. Reopen and verify persistence
        composeTestRule.onAllNodesWithText(navInventory).onFirst().performClick()
        composeTestRule.waitForIdle()
        
        // Wait for list to load
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Chicken Breast", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onAllNodesWithText("Chicken Breast", substring = true).onFirst().performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Case", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Case", substring = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("40", substring = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("oz", substring = true).onFirst().assertExists()
    }

    private fun ensureOnboarded() {
        val setupAction = composeTestRule.activity.getString(R.string.onboarding_setup_action)
        val setupExists = composeTestRule.onAllNodesWithText(setupAction).fetchSemanticsNodes().isNotEmpty()
        
        if (setupExists) {
            composeTestRule.onNodeWithText(setupAction).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("onboarding_restaurant_name").performTextInput("Test Rest")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("onboarding_next_button").performClick() // to areas
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).onFirst().performClick() // to categories
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).onFirst().performClick() // to review
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithTag("onboarding_finish_button", useUnmergedTree = true).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            // Wait for Home screen
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Dashboard", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
