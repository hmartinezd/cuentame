package com.venkoi.cuentame.feature.ingredients

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
class IngredientsNavigationTest {

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
            testStateManager.resetAll()
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
    fun createIngredientAndSave_navigatesToDetailWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Navigate to Inventory
            composeTestRule.onNodeWithTag("nav_inventory").performClick()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Tap Add
            composeTestRule.onNodeWithTag("add_ingredient_fab").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_form_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Fill form
            composeTestRule.onNodeWithTag("ingredient_name_input").performTextInput("New Ingredient")
            
            // Select Dimension (MASS)
            composeTestRule.onNodeWithTag("dimension_selector").performClick()
            composeTestRule.onNodeWithTag("dimension_item_MASS").performClick()
            
            // Select Base Unit (unit-test-1 seeded as Pound/lb)
            composeTestRule.onNodeWithTag("base_unit_selector").performClick()
            composeTestRule.onNodeWithTag("base_unit_item_unit-test-1").performClick()
            
            // Confirm Save is enabled and Save
            composeTestRule.onNodeWithTag("ingredient_form_save").performScrollTo().assertIsEnabled()
            composeTestRule.onNodeWithTag("ingredient_form_save").performClick()
            
            // List-origin creation returns to the list; open the created item to verify it.
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodes(hasText("New Ingredient")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("New Ingredient").performClick()
            composeTestRule.onNodeWithTag("ingredient_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithText("New Ingredient").assertIsDisplayed()
            
            // Back returns to the list.
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithTag("ingredient_list_screen").assertIsDisplayed()
        }
    }
}
