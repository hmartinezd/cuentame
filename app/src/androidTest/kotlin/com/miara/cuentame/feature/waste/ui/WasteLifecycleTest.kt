package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.flow.first
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
            preferencesRepository.clearAll()
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
        
        preferencesRepository.setOnboardingCompleted(true)
    }

    @Test
    fun wasteLifecycle_fullScenario() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()

            // 1. Open Waste History
            composeTestRule.openWasteHistory()
            
            // 2. Start New Waste
            composeTestRule.waitForTag("add_waste_fab")
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Fill Form
            composeTestRule.waitForTag("ingredient_selector")
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitForTag("ingredient_item_Chicken Breast")
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitForTag("area_item_Main Kitchen")
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextReplacement("3")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.waitForTag("unit_item_lb")
            composeTestRule.onNodeWithTag("unit_item_lb").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("reason_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("reason_item_SPOILED")
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // 4. Verify Preview
            composeTestRule.waitForTag("estimated_value_preview")
            composeTestRule.onNodeWithTag("estimated_value_preview").assertTextContains("$6.00", substring = true)
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("7 lb", substring = true)

            // 5. Save Draft
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // 6. Navigate away and reopen
            // After save, it navigates to WasteDetail and pops the Create Form.
            composeTestRule.waitForWasteDetail()
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            
            // Back from Detail now goes straight to List
            composeTestRule.waitForTag("waste_list_screen")
            
            // Get eventId from database (only one exists)
            val eventId = runBlocking { database.wasteDao().observeFiltered(restaurantId, null).first().first().id }
            composeTestRule.openWasteEvent(eventId)
            
            // 7. Assert exact values in Detail
            composeTestRule.onNodeWithTag("waste_detail_quantity").assertTextContains("3 lb", substring = true)
            composeTestRule.onNodeWithTag("waste_detail_base_quantity").assertTextContains("3 lb", substring = true)
            
            val reasonLabel = context.getString(WasteReason.SPOILED.toLabelRes())
            composeTestRule.onNodeWithTag("waste_detail_reason").assertTextContains(reasonLabel, substring = true)

            composeTestRule.onNodeWithTag("waste_status_chip").assertTextContains(context.getString(R.string.status_draft), substring = true, ignoreCase = true)
            composeTestRule.onNodeWithTag("waste_detail_estimated_value").assertTextContains("6.00", substring = true)
            composeTestRule.onNodeWithTag("waste_detail_current_balance").assertTextContains("10 lb", substring = true)
            composeTestRule.onNodeWithTag("waste_detail_remaining_balance").assertTextContains("7 lb", substring = true)
            
            // 8. Post
            composeTestRule.onNodeWithTag("waste_post_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitForTag("waste_post_confirm_dialog")
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 9. Verify POSTED
            composeTestRule.waitForWasteStatus(context.getString(R.string.status_posted))
            
            composeTestRule.onNodeWithTag("waste_post_confirm_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_post_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_edit_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_delete_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_void_button").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("waste_detail_estimated_value").assertTextContains("$6.00", substring = true)

            // 10. Navigate away and reopen POSTED
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForTag("waste_list")
            
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_status_chip").assertTextContains(context.getString(R.string.status_posted), substring = true)
            composeTestRule.onNodeWithTag("waste_detail_estimated_value").assertTextContains("$6.00", substring = true)

            // 11. Void Waste
            composeTestRule.onNodeWithTag("waste_void_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitForTag("waste_void_confirm_dialog")
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // 12. Verify VOIDED
            composeTestRule.waitForWasteStatus(context.getString(R.string.status_voided))
            composeTestRule.onNodeWithTag("waste_void_confirm_dialog").assertDoesNotExist()

            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForTag("waste_list")
            
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_status_chip").assertTextContains(context.getString(R.string.status_voided), substring = true)
            
            // Original data should remain
            composeTestRule.onNodeWithTag("waste_detail_quantity").assertTextContains("3 lb", substring = true)
            composeTestRule.onNodeWithTag("waste_detail_reason").assertTextContains(reasonLabel, substring = true)
            composeTestRule.onNodeWithTag("waste_detail_estimated_value").assertTextContains("$6.00", substring = true)
            
            composeTestRule.onNodeWithTag("waste_post_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_edit_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_delete_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("waste_void_button").assertDoesNotExist()
        }
    }

    @Test
    fun wasteLifecycle_negativeBalance() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            
            composeTestRule.waitForTag("add_waste_fab")
            composeTestRule.onNodeWithTag("add_waste_fab").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitForTag("ingredient_selector")
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitForTag("ingredient_item_Chicken Breast")
            composeTestRule.onNodeWithTag("ingredient_item_Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitForTag("area_item_Main Kitchen")
            composeTestRule.onNodeWithTag("area_item_Main Kitchen").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("quantity_input").performTextReplacement("12")
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.waitForTag("unit_item_lb")
            composeTestRule.onNodeWithTag("unit_item_lb").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("reason_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("reason_item_SPOILED")
            composeTestRule.onNodeWithTag("reason_item_SPOILED").performClick()
            composeTestRule.waitForIdle()
            
            // Verify negative warning
            composeTestRule.waitForTag("negative_warning_row")
            composeTestRule.onNodeWithTag("negative_warning_row").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("remaining_balance_preview").assertTextContains("-2", substring = true)

            // Save and Post
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitForWasteDetail()
            composeTestRule.onNodeWithTag("waste_post_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // Confirmation dialog with negative warning
            composeTestRule.waitForTag("waste_post_confirm_dialog")
            composeTestRule.onNodeWithTag("waste_post_confirm_dialog").assertTextContains(context.getString(R.string.negative_inventory_warning), substring = true)
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // Verify POSTED
            composeTestRule.waitForWasteStatus(context.getString(R.string.status_posted))

            // Verify resulting projection equals -2
            val projection = runBlocking { database.inventoryProjectionDao().getBalance(ingId, areaId) }
            assertThat(BigDecimal(projection!!.quantityBase).compareTo(BigDecimal("-2.0"))).isEqualTo(0)
            
            // Verify cost remains unchanged
            val costProj = runBlocking { database.ingredientCostProjectionDao().getCost(ingId) }
            assertThat(BigDecimal(costProj!!.averageUnitCostBase).compareTo(BigDecimal("2.0"))).isEqualTo(0)
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
