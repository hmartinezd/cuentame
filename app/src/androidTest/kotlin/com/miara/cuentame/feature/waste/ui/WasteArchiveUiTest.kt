package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.miara.cuentame.R
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
class WasteArchiveUiTest {

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

    private val restaurantId = "rest-archive"
    
    private val archivedAreaId = "area-archived"
    private val activeAreaId = "area-active"
    
    private val archivedIngId = "ing-archived"
    private val activeIngId = "ing-active"
    
    private val archivedOptId = "opt-archived"
    private val activeOptId = "opt-active"
    
    private val unitId = "unit-1"

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
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Archive Rest", "USD", "en", 0L, 0L, null))
        
        database.inventoryAreaDao().upsert(InventoryAreaEntity(archivedAreaId, restaurantId, "Archived Area", "archived area", 1, false, 0L, 0L, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(activeAreaId, restaurantId, "Active Area", "active area", 2, true, 0L, 0L, null))
        
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        
        database.ingredientDao().insert(IngredientEntity(archivedIngId, restaurantId, "Archived Chicken", "archived chicken", null, unitId, null, null, null, null, false, 0L, 0L, null))
        database.ingredientDao().insert(IngredientEntity(activeIngId, restaurantId, "Active Chicken", "active chicken", null, unitId, null, null, null, null, true, 0L, 0L, null))
        
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(archivedOptId, archivedIngId, "lb", "lb", null, BigDecimal.ONE, true, true, true, false, 0L, 0L, null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(activeOptId, activeIngId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
        
        preferencesRepository.setOnboardingCompleted(true)
    }

    @Test
    fun draftWithArchivedReferences_fullFlow() {
        val eventId = "event-archived"
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, archivedIngId, archivedAreaId, archivedOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)

            // 1. Verify detail shows labels
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertIsDisplayed()

            // 2. Edit Form
            composeTestRule.openWasteEdit()
            
            // 3. Change away from archived ingredient
            composeTestRule.onNodeWithTag("ingredient_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("ingredient_item_Active Chicken")
            composeTestRule.onNodeWithTag("ingredient_item_Active Chicken").performClick()
            composeTestRule.waitForIdle()
            
            // 4. Change away from archived area
            composeTestRule.onNodeWithTag("area_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("area_item_Active Area")
            composeTestRule.onNodeWithTag("area_item_Active Area").performClick()
            composeTestRule.waitForIdle()

            // 5. Persist active selections after leaving archived references
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().assertIsEnabled()
            composeTestRule.onNodeWithTag("waste_save_button").performClick()
            
            // Should be back at WasteDetail
            composeTestRule.waitForWasteDetail()
            
            // Go back to List
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForTag("waste_list")
            
            // Reopen the draft
            composeTestRule.openWasteEvent(eventId)
            
            // Assert active values persisted and NOT labeled Archived
            composeTestRule.onNodeWithText("Active Chicken").assertIsDisplayed()
            composeTestRule.onNodeWithText("Active Area").assertIsDisplayed()
            
            // Open menus and assert previous archived references are no longer offered
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForTag("ingredient_selector")
            
            // Ingredient menu
            composeTestRule.onNodeWithTag("ingredient_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("ingredient_item_Active Chicken")
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertDoesNotExist()
            composeTestRule.dismissOpenPopup("ingredient_item_Active Chicken")
            
            // Area menu
            composeTestRule.onNodeWithTag("area_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("area_item_Active Area")
            composeTestRule.onNodeWithText("Archived Area", substring = true).assertDoesNotExist()
            composeTestRule.dismissOpenPopup("area_item_Active Area")
        }
    }

    @Test
    fun missingIngredient_producesErrorState() {
        val eventId = "event-missing-ing"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, "MISSING_ING", activeAreaId, activeOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
        }
    }

    @Test
    fun missingArea_producesErrorState() {
        val eventId = "event-missing-area"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, activeIngId, "MISSING_AREA", activeOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
        }
    }

    @Test
    fun missingUnitOption_producesErrorState() {
        val eventId = "event-missing-unit"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, activeIngId, activeAreaId, "MISSING_OPT", "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
        }
    }

    @Test
    fun crossIngredientUnitOption_producesErrorState() {
        val eventId = "event-cross"
        val otherIngId = "other-ing"
        val otherOptId = "other-opt"
        runBlocking {
            database.ingredientDao().insert(IngredientEntity(otherIngId, restaurantId, "Other", "other", null, unitId, null, null, null, null, true, 0L, 0L, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(otherOptId, otherIngId, "other-lb", "other-lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

            // Draft for activeIngId but using otherOptId (which belongs to otherIngId)
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, activeIngId, activeAreaId, otherOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
        }
    }
}
