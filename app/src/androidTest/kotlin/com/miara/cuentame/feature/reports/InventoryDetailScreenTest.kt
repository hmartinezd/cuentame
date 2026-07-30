package com.miara.cuentame.feature.reports

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
class InventoryDetailScreenTest {

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
    fun inventoryDetail_exists() {
        runBlocking {
            testStateManager.seedBaseline()
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_reports", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_view_inventory_details").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_report_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_report_screen").assertIsDisplayed()
        }
    }
}
