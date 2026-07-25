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

    @Inject
    lateinit var failureBoundary: com.miara.cuentame.core.database.repository.IntegrationFailureBoundary

    @Inject
    lateinit var attachmentPermissionManager: com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager

    private val restaurantId = "rest-1"
    private val areaId = "area-1"
    private val ingId = "ing-1"
    private val unitId = "unit-1"
    private val optId = "opt-1"

    @Before
    fun setup() {
        hiltRule.inject()
        (failureBoundary as? com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary)?.reset()
        (attachmentPermissionManager as? com.miara.cuentame.core.di.ConfigurableAttachmentPermissionManager)?.shouldFail = false
        runBlocking {
            preferencesRepository.setAppLocaleTag("en")
            seedData()
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
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
        
        database.inventoryProjectionDao().upsert(com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity(
            restaurantId, ingId, areaId, "10.0", 1000L
        ))
        database.ingredientCostProjectionDao().upsert(com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity(
            restaurantId, ingId, "2.0", 1000L
        ))
    }

    @Test
    fun wasteLifecycle_fullScenario() {
        runBlocking {
            preferencesRepository.setAppLocaleTag("en")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()

            // 1. Open Waste History
            composeTestRule.onNodeWithText(context.getString(R.string.waste_history)).performClick()
            composeTestRule.waitForIdle()
            
            // 2. Start New Waste
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("add_waste_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Fill Form
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("area_item_Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextReplacement("3")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("unit_item_lb").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("unit_item_lb").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("reason_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("reason_item_SPOILED").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // 4. Verify Preview
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("estimated_value_preview").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("estimated_value_preview").assertTextContains("$6.00", substring = true)
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("7 lb", substring = true)

            // 5. Save Draft
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // 6. Navigate away and reopen
            // After save, it navigates to WasteDetail. 
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForIdle()
            
            // Back from Form
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_form_back").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_form_back").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasText("Chicken Breast") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            
            // 7. Assert exact values in Detail
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
            composeTestRule.onNodeWithText("Main Kitchen").assertIsDisplayed()
            
            // Use exact text match
            composeTestRule.onAllNodesWithText("3 lb").assertCountEquals(2)
            
            val reasonLabel = context.getString(WasteReason.SPOILED.toLabelRes())
            composeTestRule.onNodeWithText(reasonLabel).assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.status_draft)).assertIsDisplayed()
            composeTestRule.onNodeWithText("6.00", substring = true).assertIsDisplayed()
            
            // 8. Post
            composeTestRule.onNodeWithTag("waste_post_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 9. Verify POSTED
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_posted)).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("waste_post_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_edit_button").assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.delete_waste_draft)).assertDoesNotExist()
            
            // 10. Navigate away and reopen POSTED
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasText("Chicken Breast") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_posted)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("6.00", substring = true).assertIsDisplayed()

            // 11. Void Waste
            composeTestRule.onNodeWithTag("waste_void_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 12. Verify VOIDED persists
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_voided)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasText("Chicken Breast") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_voided)).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Original data should remain
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("3 lb").onFirst().assertIsDisplayed()
            composeTestRule.onNodeWithText(reasonLabel).assertIsDisplayed()
            composeTestRule.onNodeWithText("6.00", substring = true).assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("waste_post_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_edit_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_void_button").assertDoesNotExist()
        }
    }

    @Test
    fun wasteLifecycle_negativeBalance() {
        runBlocking {
            preferencesRepository.setAppLocaleTag("en")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithText(context.getString(R.string.waste_history)).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("add_waste_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("area_item_Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextReplacement("12")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("unit_item_lb").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("unit_item_lb").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("reason_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("reason_item_SPOILED").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // Verify negative warning
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.negative_inventory_warning)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("-2", substring = true)

            // Save and Post
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            composeTestRule.waitForIdle()
            
            // Confirmation dialog
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // Verify POSTED
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_posted)).fetchSemanticsNodes().isNotEmpty()
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
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithText("Test Rest").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
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
