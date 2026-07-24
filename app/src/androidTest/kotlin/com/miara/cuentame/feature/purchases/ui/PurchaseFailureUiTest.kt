package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.restaurant.Restaurant
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

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun post_failure_preserves_dialog_and_shows_snackbar() {
        runBlocking {
            val now = Instant.now()
            val restId = RestaurantId("rest_fail_test")
            db.restaurantDao().insert(Restaurant(restId, "Fail Rest", "USD", "en-US", now, now).toEntity())
            
            val areaId = InventoryAreaId("area_fail_test")
            db.inventoryAreaDao().upsert(InventoryArea(areaId, restId, "Main Kitchen", "main kitchen", 0, true, now, now).toEntity())
            
            val ingId = IngredientId("chicken_fail_test")
            db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), null, null, null, null, true, now, now).toEntity())
            db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb_fail"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now).toEntity())

            // 1. Navigate to Purchases
            composeTestRule.onAllNodesWithText(composeTestRule.activity.getString(R.string.nav_activity)).onFirst().performClick()
            composeTestRule.waitForIdle()

            // 2. Create Draft
            composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.add_purchase)).performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
            composeTestRule.waitForIdle()

            // 3. Add Line
            composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.add_line)).performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.ingredient_name)).performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.receiving_area)).performClick()
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("area_item_Main Kitchen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Main Kitchen", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.purchase_unit)).performClick()
            composeTestRule.onNodeWithTag("unit_item_Pound").performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.quantity)).performTextInput("1")
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.line_total)).performTextInput("10")
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
            composeTestRule.waitForIdle()

            // 4. Inject malformed history via DAO to make POST fail
            val receiptId = db.purchaseDao().observeFilteredReceipts(restId.value, null, null, null).first().first().id
            db.inventoryMovementDao().insert(com.miara.cuentame.core.database.entity.InventoryMovementEntity(
                "mov_bad_fail", restId.value, ingId.value, areaId.value, com.miara.cuentame.core.model.inventory.InventoryMovementType.PURCHASE.name,
                "1", "10", "10", now.toEpochMilli(), com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name,
                receiptId, "purchase-post:${receiptId}:bad", "bad_line", null, 0
            ))

            // 5. Try to Post
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.post_purchase)).performClick()
            composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.ok), ignoreCase = true)).performClick()
            composeTestRule.waitForIdle()

            // 6. Verify Dialog remains and error is shown
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.posting_warning)).assertIsDisplayed()
            val errorText = composeTestRule.activity.getString(R.string.error_malformed_history)
            composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
            
            // 7. Dismiss dialog manually and verify we are still DRAFT
            composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.cancel), ignoreCase = true)).performClick()
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.status_draft)).assertIsDisplayed()
        }
    }
}
