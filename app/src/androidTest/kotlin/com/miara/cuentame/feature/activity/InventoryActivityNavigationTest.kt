package com.miara.cuentame.feature.activity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InventoryActivityNavigationTest {

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
    fun activityNavigation_fromHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Activity from Home
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }

    @Test
    fun activityNavigation_fromIngredientDetail() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Ingredients
            composeTestRule.onNodeWithTag("nav_inventory").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list")).fetchSemanticsNodes().isNotEmpty()
            }

            // 2. Open an Ingredient (Chicken seeded)
            composeTestRule.onNodeWithTag("ingredient_item_Chicken").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 3. View Activity
            composeTestRule.onNodeWithTag("ingredient_view_activity").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify prefilter
            composeTestRule.onNodeWithTag("inventory_activity_filter_ingredient").assertTextContains("Chicken")
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }

    @Test
    fun activityNavigation_fromAreaDetail() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Settings
            composeTestRule.onNodeWithTag("nav_settings").performClick()

            // 2. Open Areas
            composeTestRule.onNodeWithTag("settings_areas").performClick()

            // 3. Open an Area (Kitchen seeded)
            composeTestRule.onNodeWithText("Kitchen").performClick()

            // 4. View Activity
            composeTestRule.onNodeWithTag("area_view_activity").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify prefilter
            composeTestRule.onNodeWithTag("inventory_activity_filter_area").assertTextContains("Kitchen")
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }

    @Test
    fun activityNavigation_listToDetailAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Open Activity
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Search
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("Tomato")

            // 2. Open Detail
            composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).onFirst().performClick() // Need a better way to find items
            // Actually, let's use a simpler check for now if I can't find a good matcher for prefix test tags
            // composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_", substring = true)).onFirst().performClick()
            // Try this instead:
            // composeTestRule.onNode(hasTestTag("inventory_activity_row_").and(hasAnyChild(hasText("Tomato")))).performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 3. Go back
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // 4. Verify search is preserved
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextContains("Tomato")
        }
    }
}
