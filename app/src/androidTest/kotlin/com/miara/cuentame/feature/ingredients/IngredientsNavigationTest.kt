package com.miara.cuentame.feature.ingredients

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
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Navigate to Inventory
            composeTestRule.onNodeWithTag("nav_inventory").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Tap Add
            composeTestRule.onNodeWithTag("add_ingredient_button").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_form_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Fill form
            composeTestRule.onNodeWithTag("ingredient_name_input").performTextInput("New Ingredient")
            
            // Save
            composeTestRule.onNodeWithTag("save_ingredient_button").performClick()
            
            // Verify navigation to detail
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("ingredient_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithText("New Ingredient").assertIsDisplayed()
        }
    }
}
