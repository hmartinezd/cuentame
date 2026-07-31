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
class ReportsNavigationTest {

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
    fun reportsRangePreservation_navigatesCorrectly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Select 90 days on Home (this usually propagates to Reports if shared, or we just test it on Reports screen)
            // The prompt says: "Reports overview with 90-day range -> Waste report opens with LAST_90_DAYS"
            
            // Navigate to Reports
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Select 7 days on Reports screen
            composeTestRule.onNodeWithTag("reports_range_7").performClick()

            // Open Purchase Report
            composeTestRule.onNodeWithTag("reports_view_purchase_details").performClick()

            // Verify Detail Report shows 7 days
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_report_range_selector")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("purchase_report_range_7").assertIsSelected()
        }
    }
}
