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
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class PurchaseUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var db: RestaurantInventoryDatabase

    @Before
    fun setup() {
        hiltRule.inject()
        seedData()
    }

    private fun seedData() = runBlocking {
        val now = Instant.now()
        val restId = RestaurantId("rest_ui_test")
        db.restaurantDao().insert(Restaurant(restId, "Test Restaurant", "USD", "en-US", now, now).toEntity())
        
        val areaId = InventoryAreaId("area_ui_test")
        db.inventoryAreaDao().upsert(InventoryArea(areaId, restId, "Main Kitchen", "main kitchen", 0, true, now, now).toEntity())
        
        val ingId = IngredientId("chicken_ui_test")
        db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), null, null, null, null, true, now, now).toEntity())
        
        db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb_ui_test"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now).toEntity())
        db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_case_ui_test"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now).toEntity())
    }

    @Test
    fun complete_purchase_lifecycle() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Purchases
        val navActivity = composeTestRule.activity.getString(R.string.nav_activity)
        composeTestRule.onAllNodesWithText(navActivity).onFirst().performClick()
        composeTestRule.waitForIdle()

        // 2. Create Draft
        val addPurchase = composeTestRule.activity.getString(R.string.add_purchase)
        composeTestRule.onNodeWithContentDescription(addPurchase).performClick()
        composeTestRule.waitForIdle()

        // 3. Save Header
        val invoiceLabel = composeTestRule.activity.getString(R.string.invoice_number)
        composeTestRule.onNodeWithText(invoiceLabel).performTextInput("INV-UI-TEST")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
        composeTestRule.waitForIdle()

        // 4. Add Line
        val addLine = composeTestRule.activity.getString(R.string.add_line)
        composeTestRule.onNodeWithContentDescription(addLine).performClick()
        composeTestRule.waitForIdle()

        // 5. Fill Line Form
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.ingredient_name)).performClick()
        composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.receiving_area)).performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Main Kitchen").fetchSemanticsNodes().size > 1
        }
        composeTestRule.onAllNodesWithText("Main Kitchen").onLast().performClick()
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.purchase_unit)).performClick()
        composeTestRule.onNodeWithTag("unit_item_Case").performClick()

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.quantity)).performTextInput("2")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.line_total)).performTextInput("160")
        
        // Verify Preview
        composeTestRule.onNodeWithText("= 80 lb").assertIsDisplayed()

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
        composeTestRule.waitForIdle()

        // 6. Navigate away and reopen
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("INV-UI-TEST", substring = true).performClick()
        composeTestRule.waitForIdle()

        // 7. Verify persisted line
        composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 Case", substring = true).assertIsDisplayed()

        // 8. Post Purchase
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.post_purchase)).performClick()
        // Confirmation dialog
        composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.ok), ignoreCase = true)).performClick()
        composeTestRule.waitForIdle()

        // 9. Verify Posted
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.status_posted)).assertIsDisplayed()

        // 10. Navigate away and reopen POSTED
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("INV-UI-TEST", substring = true).performClick()
        composeTestRule.waitForIdle()

        // 11. Void Purchase
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.void_purchase)).performClick()
        // Confirmation dialog
        composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.ok), ignoreCase = true)).performClick()
        composeTestRule.waitForIdle()

        // 12. Verify Voided
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.status_voided)).assertIsDisplayed()
    }
}
