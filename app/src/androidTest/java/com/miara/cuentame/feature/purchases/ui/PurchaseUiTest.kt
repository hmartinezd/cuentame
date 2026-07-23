package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        val restId = RestaurantId("rest_test")
        db.restaurantDao().insert(Restaurant(restId, "Test Restaurant", "USD", "en-US", now, now).toEntity())
        
        val areaId = InventoryAreaId("area_test")
        db.inventoryAreaDao().upsert(InventoryArea(areaId, restId, "Main Kitchen", "main kitchen", 0, true, now, now).toEntity())
        
        val ingId = IngredientId("chicken_test")
        db.ingredientDao().insert(Ingredient(ingId, restId, "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), null, null, null, null, true, now, now).toEntity())
        
        db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_lb"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now).toEntity())
        db.ingredientUnitOptionDao().insert(IngredientUnitOption(IngredientUnitOptionId("opt_case"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now).toEntity())
    }

    @Test
    fun complete_purchase_lifecycle() {
        // Skip onboarding by having restaurant seeded
        composeTestRule.waitForIdle()

        // 1. Navigate to Activity (Purchases)
        val navActivity = composeTestRule.activity.getString(R.string.nav_activity)
        composeTestRule.onAllNodesWithText(navActivity).onFirst().performClick()
        composeTestRule.waitForIdle()

        // 2. Create Draft
        val addPurchase = composeTestRule.activity.getString(R.string.add_purchase)
        composeTestRule.onNodeWithContentDescription(addPurchase).performClick()
        composeTestRule.waitForIdle()

        // 3. Save Header
        val invoiceLabel = composeTestRule.activity.getString(R.string.invoice_number)
        composeTestRule.onNodeWithText(invoiceLabel).performTextInput("INV-TEST-999")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
        composeTestRule.waitForIdle()

        // 4. Add Line
        val addLine = composeTestRule.activity.getString(R.string.add_line)
        composeTestRule.onNodeWithContentDescription(addLine).performClick()
        composeTestRule.waitForIdle()

        // 5. Fill Line Form
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.ingredient_name)).performClick()
        composeTestRule.onNodeWithText("Chicken Breast").performClick()
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.receiving_area)).performClick()
        composeTestRule.onNodeWithText("Main Kitchen").performClick()
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.purchase_unit)).performClick()
        composeTestRule.onNodeWithText("Case").performClick()

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.quantity)).performTextInput("2")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.line_total)).performTextInput("160")
        
        // Verify Preview (Using formatters)
        // "= 80 lb"
        composeTestRule.onNodeWithText("= 80 lb").assertExists()
        // "$2.00 per lb"
        // Wait, formatCurrency for 2 in USD is probably "$2.00" or similar depending on locale
        // Let's use a looser check or exact if we know the locale.
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_save)).performClick()
        composeTestRule.waitForIdle()

        // 6. Post Purchase
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.post_purchase)).performClick()
        // Confirmation dialog
        composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.ok), ignoreCase = true)).performClick()
        composeTestRule.waitForIdle()

        // 7. Verify Posted (Detail screen)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.status_posted)).assertExists()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.void_purchase)).assertExists()
        
        // 8. Void Purchase
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.void_purchase)).performClick()
        // Confirmation dialog
        composeTestRule.onNode(hasText(composeTestRule.activity.getString(android.R.string.ok), ignoreCase = true)).performClick()
        composeTestRule.waitForIdle()

        // 9. Verify Voided
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.status_voided)).assertExists()
    }
}
