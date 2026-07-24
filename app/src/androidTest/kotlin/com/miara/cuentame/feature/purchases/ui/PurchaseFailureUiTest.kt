package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
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
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            
            val now = Instant.now()
            val restId = RestaurantId("rest_1")
            db.restaurantDao().insert(Restaurant(restId, "Fail Rest", "USD", "en-US", now, now, null).toEntity())
            db.inventoryAreaDao().upsert(InventoryArea(InventoryAreaId("area_1"), restId, "Main Kitchen", "main kitchen", 0, true, now, now, null).toEntity())

            preferencesRepository.setAppLocaleTag("en-US")
            preferencesRepository.setOnboardingCompleted(true)
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

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()
            
            // Wait for loading to finish
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
            }

            // Wait for Home screen to load
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("nav_home", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Navigate to Purchases
            composeTestRule.onNodeWithTag("nav_activity", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            
            // 2. Create Draft
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("add_purchase_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_purchase_fab").performClick()
            composeTestRule.onNodeWithTag("purchase_header_save").performClick()
            
            // 3. Add Line
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithContentDescription("Add Line").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Add Line").performClick()
            
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast", useUnmergedTree = true).performClick()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_item_Pound").performClick()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextInput("1")
            composeTestRule.onNodeWithTag("total_price_input").performTextInput("10")
            composeTestRule.onNodeWithTag("purchase_line_save").performClick()
            
            // 4. Inject malformed history via DAO to make POST fail
            runBlocking {
                val receiptId = db.purchaseDao().observeFilteredReceipts(restId.value, null, null, null).first().first().id
                db.inventoryMovementDao().insert(InventoryMovementEntity(
                    "mov_bad_fail", restId.value, ingId.value, "area_1", InventoryMovementType.PURCHASE.name,
                    "1", "10", "10", now.toEpochMilli(), SourceDocumentType.PURCHASE_RECEIPT.name,
                    receiptId, "purchase-post:${receiptId}:bad", "bad_line", null, 0
                ))
            }

            // 5. Try to Post
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithText("Post Receipt").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Post Receipt").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Confirm").performClick()
            composeTestRule.waitForIdle()
            
            // 6. Verify Dialog remains or error is shown
            composeTestRule.waitUntil(20000) {
                // Check for snackbar text
                composeTestRule.onAllNodesWithText("Malformed inventory history", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            
            // 7. Verify we are still DRAFT
            composeTestRule.onNodeWithText("DRAFT").assertIsDisplayed()
        }
    }
}
