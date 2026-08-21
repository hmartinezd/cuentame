package com.venkoi.restaurantops.feature.preparations

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.restaurantops.MainActivity
import com.venkoi.restaurantops.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PreparationRecipeNavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val timeoutRule: Timeout = Timeout.seconds(60)

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
    fun navigateToPreparationRecipes_fromHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            composeTestRule.onNodeWithTag("preparation_recipe_list_screen").assertIsDisplayed()
        }
    }

    @Test
    fun createRecipeAndBack_returnsToList() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").performClick()
            
            composeTestRule.onNodeWithTag("preparation_recipe_editor_screen").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("preparation_back_button").performClick()
            composeTestRule.onNodeWithTag("preparation_recipe_list_screen").assertIsDisplayed()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("home_dashboard_list").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
