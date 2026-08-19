package com.miara.cuentame.feature.purchases

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
class PurchasesNavigationTest {

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
    fun purchaseLifecycle_navigatesCorrectly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Purchases are created from the Home quick action after the unified Activity navigation change.
            composeTestRule.onNodeWithTag("new_purchase_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_draft_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Save Header (obtain draft)
            composeTestRule.onNodeWithTag("purchase_invoice_input").performTextInput("INV-TEST-1")
            composeTestRule.onNodeWithTag("purchase_header_save").performClick()

            // Wait for draft status (Ready state will show lines section)
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasContentDescription("Add Line")).fetchSemanticsNodes().isNotEmpty()
            }

            // 4. Add Line
            composeTestRule.onNodeWithContentDescription("Add Line").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_selector")).fetchSemanticsNodes().isNotEmpty()
            }

            // Select Ingredient (Chicken seeded)
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Chicken").performClick()

            // Select Area (Storage seeded)
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onNodeWithTag("area_item_Storage").performClick()

            // Select Unit (lb seeded)
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_item_lb").performClick()

            composeTestRule.onNodeWithTag("quantity_input").performTextInput("5")
            composeTestRule.onNodeWithTag("total_price_input").performTextInput("10.50")

            // Save Line
            composeTestRule.onNodeWithTag("purchase_line_save").performClick()

            // 5. Post
            composeTestRule.onNodeWithTag("purchase_draft_list")
                .performScrollToNode(hasTestTag("purchase_post_button"))
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_post_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_post_button").performClick()

            // Confirm Post
            composeTestRule.onNodeWithTag("purchase_post_confirm_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()

            // 6. Verify Detail
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithTag("purchase_invoice_number").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_invoice_number").assertTextContains("INV-TEST-1", substring = true)
        }
    }
}
