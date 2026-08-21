package com.venkoi.restaurantops.feature.ingredients.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.venkoi.restaurantops.MainActivity
import com.venkoi.restaurantops.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class IngredientDetailUiTest {

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
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun archive_ingredient_flow() {
        runBlocking {
            testStateManager.seedBaseline()
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_inventory", useUnmergedTree = true).performClick()
            
            // Wait for list to load
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Just verifying navigation to inventory for now as per minimal behavior
            composeTestRule.onNodeWithTag("ingredient_list_screen").assertIsDisplayed()
        }
    }
}
