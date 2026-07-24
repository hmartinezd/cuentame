package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class WasteLifecycleTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    private val restaurantId = "rest-1"
    private val areaId = "area-1"
    private val ingId = "ing-1"
    private val unitId = "unit-1"
    private val optId = "opt-1"

    @Before
    fun setup() {
        hiltRule.inject()
        seedData()
    }

    private fun seedData() = runBlocking {
        database.clearAllTables()
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en", 0L, 0L, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Main Kitchen", "main kitchen", 1, true, 0L, 0L, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken Breast", "chicken breast", null, unitId, null, null, null, null, true, 0L, 0L, null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

        // Seed a purchase to have initial balance and cost
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "purch-1",
                restaurantId = restaurantId,
                ingredientId = ingId,
                areaId = areaId,
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10.0",
                unitCostBaseSnapshot = "2.0",
                totalValueSnapshot = "20.0",
                effectiveAt = 0L,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "doc-1",
                sourceLineId = "line-1",
                sourceOperationId = "op-1",
                reversalOfMovementId = null,
                createdAt = 0L
            )
        )
    }

    @Test
    fun wasteLifecycle_fullScenario() {
        // Wait for Home
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("home_screen"), 15000)

        // 1. Open Waste History
        composeTestRule.onNodeWithTag("view_waste_button").performClick()
        
        // 2. Start New Waste
        composeTestRule.onNodeWithTag("add_waste_fab").performClick()
        
        // 3. Fill Form
        composeTestRule.onNodeWithTag("ingredient_selector").performClick()
        composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
        
        composeTestRule.onNodeWithTag("area_selector").performClick()
        composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
        
        composeTestRule.onNodeWithTag("quantity_input").performTextInput("3")
        
        composeTestRule.onNodeWithTag("reason_selector").performClick()
        composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
        
        // 4. Verify Preview (3 lb, current 10, remaining 7, value $6)
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("estimated_value_preview"), 10000)
        composeTestRule.onNodeWithTag("current_balance_preview").assertTextContains("10", substring = true)

        // 5. Save Draft
        composeTestRule.onNodeWithTag("waste_save_button").performClick()
        
        // 6. Verify and Click Post (Detail Screen)
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("waste_post_button"), 15000)
        composeTestRule.onNodeWithTag("waste_post_button").performClick()
        
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_confirm)).performClick()
        
        // 7. Verify POSTED
        composeTestRule.waitUntilExactlyOneExists(hasText(composeTestRule.activity.getString(R.string.status_posted), substring = true), 15000)
        
        // 8. Void Waste
        composeTestRule.onNodeWithTag("waste_void_button").performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.action_confirm)).performClick()
        
        // 9. Verify VOIDED
        composeTestRule.waitUntilExactlyOneExists(hasText(composeTestRule.activity.getString(R.string.status_voided), substring = true), 15000)
    }
}
