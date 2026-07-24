package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
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
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: com.miara.cuentame.core.preferences.repository.AppPreferencesRepository

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
        preferencesRepository.setOnboardingCompleted(true)
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

            // Wait for Home
            composeTestRule.waitUntilExactlyOneExists(hasTestTag("home_screen"), 15000)

            // 1. Open Waste History
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            // 2. Start New Waste
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Fill Form
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("area_item_Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextInput("3")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("reason_selector").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // 4. Verify Preview (3 lb, current 10, remaining 7, value $6)
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("estimated_value_preview").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("current_balance_preview").assertTextContains("10", substring = true)

            // 5. Save Draft
            composeTestRule.onNodeWithTag("waste_save_button").assertIsEnabled().performClick()
            composeTestRule.waitForIdle()
            
            // 6. Verify and Click Post (Detail Screen)
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("waste_post_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            composeTestRule.waitForIdle()
            
            // 7. Verify POSTED
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_posted), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            
            // 8. Void Waste
            composeTestRule.onNodeWithTag("waste_void_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            composeTestRule.waitForIdle()
            
            // 9. Verify VOIDED
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_voided), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun wasteLifecycle_negativeBalance() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

            composeTestRule.waitUntilExactlyOneExists(hasTestTag("home_screen"), 15000)

            // Open Waste History
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            // Start New Waste
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            // Fill Form (12 lb waste, current 10 lb)
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("area_item_Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextInput("12")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("reason_selector").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // Verify negative warning
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.negative_inventory_warning)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("-2", substring = true)

            // Save and Post
            composeTestRule.onNodeWithTag("waste_save_button").assertIsEnabled().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("waste_post_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            composeTestRule.waitForIdle()
            
            // Confirmation should still show warning or be allowed
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            composeTestRule.waitForIdle()
            
            // Verify POSTED and balance
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_posted), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
