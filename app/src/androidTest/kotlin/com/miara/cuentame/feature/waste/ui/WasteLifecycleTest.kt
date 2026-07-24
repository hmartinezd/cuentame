package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
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
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
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
    lateinit var preferencesRepository: AppPreferencesRepository

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

    @org.junit.After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
        }
    }

    private fun seedData() = runBlocking {
        database.clearAllTables()
        preferencesRepository.setOnboardingCompleted(true)
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en", 0L, 0L, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Main Kitchen", "main kitchen", 1, true, 0L, 0L, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken Breast", "chicken breast", null, unitId, null, null, null, null, true, 0L, 0L, null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

        // Seed 10 lb at $2/lb
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
                effectiveAt = 1000L,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "doc-1",
                sourceLineId = "line-1",
                sourceOperationId = "op-1",
                reversalOfMovementId = null,
                createdAt = 1000L
            )
        )
    }

    @Test
    fun wasteLifecycle_fullScenario() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

            waitForHome()

            // 1. Open Waste History
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            
            // 2. Start New Waste
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            
            // 3. Fill Form
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextInput("3")
            
            composeTestRule.onNodeWithTag("reason_selector").performClick()
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            
            // 4. Verify Preview
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("estimated_value_preview").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("current_balance_preview").assertTextContains("10 lb", substring = true)
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("7 lb", substring = true)
            composeTestRule.onNodeWithTag("estimated_value_preview").assertTextContains("$6.00", substring = true)

            // 5. Save Draft
            composeTestRule.onNodeWithTag("waste_save_button").performClick()
            
            // 6. Navigate away and reopen
            composeTestRule.onNodeWithTag("waste_form_back").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_list").onChildAt(0).performClick()
            
            // 7. Assert exact values in Detail
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
            composeTestRule.onNodeWithText("Main Kitchen").assertIsDisplayed()
            composeTestRule.onNodeWithText("3", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("lb", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("status_chip").assertTextContains(context.getString(R.string.status_draft), substring = true)
            
            // Verify reason label
            val reasonLabel = context.getString(WasteReason.SPOILED.toLabelRes())
            composeTestRule.onNodeWithText(reasonLabel).assertIsDisplayed()

            // 8. Post
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.action_confirm))).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            
            // 9. Verify POSTED and mutation controls absent
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_posted), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.delete_waste_draft)).assertDoesNotExist()
            
            // 10. Navigate away and reopen POSTED
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_list").onChildAt(0).performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_posted), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("$6.00", substring = true).assertIsDisplayed()

            // 11. Void Waste
            composeTestRule.onNodeWithTag("waste_void_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.action_confirm))).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            
            // 12. Verify VOIDED persists
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_voided), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_list").onChildAt(0).performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_voided), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Original data should remain
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
            composeTestRule.onNodeWithText("3", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("lb", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun wasteLifecycle_negativeBalance() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextInput("12")
            composeTestRule.onNodeWithTag("reason_selector").performClick()
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            
            // Verify negative warning
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.negative_inventory_warning)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("-2 lb", substring = true)

            // Save and Post
            composeTestRule.onNodeWithTag("waste_save_button").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("waste_post_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            
            // Confirmation should still show warning
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasAnyAncestor(isDialog()) and hasText(context.getString(R.string.negative_inventory_warning))).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            
            // Verify POSTED
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasTestTag("status_chip") and hasText(context.getString(R.string.status_posted), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify resulting projection equals -2
            val projection = runBlocking { database.inventoryProjectionDao().getBalance(ingId, areaId) }
            assertThat(BigDecimal(projection!!.quantityBase).compareTo(BigDecimal("-2.0"))).isEqualTo(0)
            
            // Verify cost remains unchanged
            val costProj = runBlocking { database.ingredientCostProjectionDao().getCost(ingId) }
            assertThat(BigDecimal(costProj!!.averageUnitCostBase).compareTo(BigDecimal("2.0"))).isEqualTo(0)
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

fun WasteReason.toLabelRes(): Int = when (this) {
    WasteReason.EXPIRED -> R.string.reason_expired
    WasteReason.SPOILED -> R.string.reason_spoiled
    WasteReason.PREPARATION_ERROR -> R.string.reason_preparation_error
    WasteReason.OVERPRODUCTION -> R.string.reason_overproduction
    WasteReason.DROPPED_OR_DAMAGED -> R.string.reason_dropped_or_damaged
    WasteReason.CUSTOMER_RETURN -> R.string.reason_customer_return
    WasteReason.QUALITY_REJECTION -> R.string.reason_quality_rejection
    WasteReason.OTHER -> R.string.reason_other
}
