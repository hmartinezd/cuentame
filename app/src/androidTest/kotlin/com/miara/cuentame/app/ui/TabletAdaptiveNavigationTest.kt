package com.miara.cuentame.app.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class TabletAdaptiveNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject lateinit var testStateManager: TestStateManager

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
        }
    }

    @After
    fun tearDown() = runBlocking { testStateManager.resetAll() }

    @Test
    fun nonCompactNavigation_usesAdaptiveSideNavigation_andPreservesScreenAcrossRotation() {
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(30_000) {
                sideNavigationNodeCount() > 0
            }
            composeTestRule.onNodeWithTag("top_level_bottom_bar").assertDoesNotExist()

            composeTestRule.onNodeWithTag("nav_inventory").performClick()
            composeTestRule.waitUntil(30_000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list_screen"))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("ingredient_list_screen").assertExists()

            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("ingredient_list_screen").assertExists()
            assertTrue(sideNavigationNodeCount() > 0)
        }
    }

    private fun sideNavigationNodeCount(): Int =
        composeTestRule.onAllNodes(hasTestTag("top_level_navigation_rail"))
            .fetchSemanticsNodes().size +
            composeTestRule.onAllNodes(hasTestTag("top_level_navigation_sidebar"))
                .fetchSemanticsNodes().size
}
