package com.venkoi.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.mapper.toEntity
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.inventory.InventoryArea
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import com.venkoi.cuentame.feature.waste.ui.waitForTag
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            db.unitDao().insertSeedUnits(com.venkoi.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            
            val now = Instant.now()
            val restId = RestaurantId("rest_1")
            db.restaurantDao().insert(Restaurant(restId, "Test Restaurant", "USD", "en-US", now, now, null).toEntity())
            db.inventoryAreaDao().upsert(InventoryArea(InventoryAreaId("area_1"), restId, "Main Kitchen", "main kitchen", 0, true, now, now, null).toEntity())
            
            preferencesRepository.setAppLocaleTag("en-US")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }


    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    private fun waitForPurchaseStatus(expected: String) {
        composeTestRule.waitUntil(20_000) {
            composeTestRule.onAllNodes(
                hasTestTag("purchase_status_chip") and
                    hasText(expected, substring = true, ignoreCase = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun complete_purchase_lifecycle() {
        val testId = UUID.randomUUID().toString().take(8)
        val invoiceNum = "INV-$testId"
        
        var receiptId: PurchaseReceiptId? = null
        
        runBlocking {
            val restId = RestaurantId("rest_1")
            val ingId = IngredientId("chicken_$testId")
            val now = Instant.now()
            db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_1"), null, null, null, true, now, now, null).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb_$testId"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_case_$testId"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now, null).toEntity())
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()

            // Purchases are created from the Home quick action after the unified Activity navigation change.
            composeTestRule.onNodeWithTag("new_purchase_button", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            
            // Save Header
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("purchase_invoice_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_invoice_input").performTextInput(invoiceNum)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("purchase_header_save").performClick()
            composeTestRule.waitForIdle()
            
            // Capture receiptId
            val entityId = runBlocking { db.purchaseDao().observeFilteredReceipts("rest_1", null, null, null).first().first().id }
            receiptId = PurchaseReceiptId(entityId)

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
            composeTestRule.onNodeWithTag("purchase_draft_back").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_item_${receiptId!!.value}").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_${receiptId!!.value}").performClick()
            composeTestRule.waitForIdle()
            
            // 7. Verify persisted line
            composeTestRule.waitForTag("purchase_draft_screen")
            composeTestRule.waitForTag("purchase_draft_list")
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("2 Case", substring = true).performScrollTo().assertIsDisplayed()

            // 8. Post Purchase
            composeTestRule.onNodeWithTag("purchase_draft_list").performScrollToNode(hasTestTag("purchase_post_button"))
            composeTestRule.onNodeWithTag("purchase_post_button").assertIsEnabled().performClick()
            
            // Confirmation dialog
            composeTestRule.waitForTag("purchase_post_confirm_dialog")
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 9. Verify Posted
            composeTestRule.waitForTag("purchase_detail_screen")
            waitForPurchaseStatus(context.getString(R.string.status_posted))
            composeTestRule.onNodeWithTag("purchase_post_confirm_dialog").assertDoesNotExist()

            // 10. Navigate away and reopen POSTED
            composeTestRule.onNodeWithTag("purchase_detail_back").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("purchase_item_${receiptId!!.value}").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_${receiptId!!.value}").performClick()
            composeTestRule.waitForIdle()
            
            // 11. Void Purchase
            composeTestRule.waitForTag("purchase_detail_screen")
            val detailScrollable = composeTestRule.onNode(hasScrollAction())
            detailScrollable.performScrollToNode(hasTestTag("purchase_void_button"))
            composeTestRule.onNodeWithTag("purchase_void_button").performClick()
            composeTestRule.waitForIdle()
            
            // Confirmation dialog
            composeTestRule.waitForTag("purchase_void_confirm_dialog")
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 12. Verify Voided
            waitForPurchaseStatus(context.getString(R.string.status_voided))
            composeTestRule.onNodeWithTag("purchase_void_confirm_dialog").assertDoesNotExist()
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
