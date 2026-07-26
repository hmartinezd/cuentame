package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
class PurchaseUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var db: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            
            val now = Instant.now()
            val restId = RestaurantId("rest_1")
            db.restaurantDao().insert(Restaurant(restId, "Test Restaurant", "USD", "en-US", now, now, null).toEntity())
            db.inventoryAreaDao().upsert(InventoryArea(InventoryAreaId("area_1"), restId, "Main Kitchen", "main kitchen", 0, true, now, now, null).toEntity())
            
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }


    @org.junit.After
    fun teardown() {
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
        }
    }

    @Test
    fun complete_purchase_lifecycle() {
        val testId = UUID.randomUUID().toString().take(8)
        val invoiceNum = "INV-$testId"
        
        runBlocking {
            val restId = RestaurantId("rest_1")
            val ingId = IngredientId("chicken_$testId")
            val now = Instant.now()
            db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_1"), null, null, null, true, now, now, null).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb_$testId"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_case_$testId"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now, null).toEntity())
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Navigate to Activity (Purchases)
            composeTestRule.onNodeWithTag("nav_activity", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            
            // 2. Create Draft
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("add_purchase_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_purchase_fab").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Save Header
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("purchase_invoice_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_invoice_input").performTextInput(invoiceNum)
            composeTestRule.onNodeWithTag("purchase_header_save").performClick()
            composeTestRule.waitForIdle()
            
            // 4. Add Line
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithContentDescription("Add Line").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Add Line").performClick()
            composeTestRule.waitForIdle()
            
            // 5. Fill Line Form
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("unit_item_Case").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("unit_item_Case").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("quantity_input").performTextInput("2")
            composeTestRule.onNodeWithTag("total_price_input").performTextInput("160")
            
            // Verify Preview
            composeTestRule.onNodeWithText("= 80", substring = true).assertIsDisplayed()

            composeTestRule.onNodeWithTag("purchase_line_save").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // 6. Navigate away and reopen
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_draft_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(invoiceNum, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(invoiceNum, substring = true).performClick()
            composeTestRule.waitForIdle()
            
            // 7. Verify persisted line
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
            composeTestRule.onNodeWithText("2 Case", substring = true).assertIsDisplayed()

            // 8. Post Purchase
            composeTestRule.onNodeWithTag("purchase_draft_list").performScrollToNode(hasTestTag("purchase_post_button"))
            composeTestRule.onNodeWithTag("purchase_post_button").performClick()
            // Confirmation dialog
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_post_confirm_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 9. Verify Posted
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("POSTED")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("POSTED").assertIsDisplayed()

            // 10. Navigate away and reopen POSTED
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(invoiceNum, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(invoiceNum, substring = true).performClick()
            composeTestRule.waitForIdle()
            
            // 11. Void Purchase
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            // Use a scroll explicitly if the button is at the bottom
            composeTestRule.onNodeWithText("Void Purchase").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            // Confirmation dialog
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_void_confirm_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 12. Verify Voided
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("VOIDED")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("VOIDED").assertIsDisplayed()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
