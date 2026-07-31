package com.miara.cuentame.feature.counts

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
class StockCountNavigationTest {

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
    fun startCountAndOpenArea_loadsReadyState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Navigate to Count
            composeTestRule.onNodeWithTag("nav_count").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("stock_count_list_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Start New Count
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("stock_count_start_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Select an area (testStateManager seeds "Storage")
            composeTestRule.onNodeWithText("Storage").performClick()
            
            // Start
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            
            // Wait for Count Draft
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("count_detail_name")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Open Area
            composeTestRule.onNodeWithText("Storage").performClick()
            
            // Wait for Area screen
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_search")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify it's Ready and not InvalidRoute
            composeTestRule.onNodeWithTag("ingredient_search").assertIsDisplayed()
            composeTestRule.onNodeWithText("We couldn't open this inventory count").assertDoesNotExist()
        }
    }
}
