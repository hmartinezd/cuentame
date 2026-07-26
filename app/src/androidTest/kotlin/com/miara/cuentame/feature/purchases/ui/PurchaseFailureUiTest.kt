package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.waste.ui.waitForHomeReady
import com.miara.cuentame.feature.waste.ui.waitForTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class PurchaseFailureUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
            db.restaurantDao().insert(Restaurant(restId, "Fail Rest", "USD", "en-US", now, now, null).toEntity())
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
    fun post_failure_preserves_dialog_and_shows_snackbar() {
        val now = Instant.now()
        val restId = RestaurantId("rest_1")
        val ingId = IngredientId("chicken_fail_test")
        
        runBlocking {
            db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_1"), null, null, null, true, now, now, null).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb_fail"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity())
        }

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.waitForHomeReady()
        
        // 1. Navigate to Activity (Purchases)
        composeTestRule.onNodeWithTag("nav_activity", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // 2. Start New Purchase
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("add_purchase_fab").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_purchase_fab").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("purchase_header_save").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("purchase_invoice_input").performTextInput("FAIL-123")
        composeTestRule.onNodeWithTag("purchase_header_save").performClick()
        composeTestRule.waitForIdle()
        
        // 3. Add Line
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithContentDescription("Add Line").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Add Line").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ingredient_selector").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Chicken Breast", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("area_selector").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Main Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Main Kitchen").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("unit_selector").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("unit_item_Pound").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("unit_item_Pound").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("quantity_input").performTextInput("1")
        composeTestRule.onNodeWithTag("total_price_input").performTextInput("10")
        composeTestRule.onNodeWithTag("purchase_line_save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        
        // 4. Inject malformed history via DAO to make POST fail
        runBlocking {
            val entityId = db.purchaseDao().observeFilteredReceipts(restId.value, null, null, null).first().first().id
            db.inventoryMovementDao().insert(InventoryMovementEntity(
                "mov_bad_fail", restId.value, ingId.value, "area_1", InventoryMovementType.PURCHASE.name,
                "1", "10", "10", now.toEpochMilli(), SourceDocumentType.PURCHASE_RECEIPT.name,
                entityId, "purchase-post:${entityId}:bad", "bad_line", null, 0
            ))
        }

        // 5. Try to Post
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("purchase_draft_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("purchase_draft_list").performScrollToNode(hasTestTag("purchase_post_button"))
        composeTestRule.onNodeWithTag("purchase_post_button").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
        composeTestRule.waitForIdle()
        
        // 6. Verify Dialog remains
        composeTestRule.onNodeWithTag("purchase_post_confirm_dialog").assertIsDisplayed()
        
        // Verify error is shown
        val errorText = context.getString(R.string.error_malformed_history)
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodes(hasText(errorText, substring = true, ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // 7. Verify we are still DRAFT
        composeTestRule.onNodeWithTag("purchase_draft_screen").assertIsDisplayed()
    }
}
