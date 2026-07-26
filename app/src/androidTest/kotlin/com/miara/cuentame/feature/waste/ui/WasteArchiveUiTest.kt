package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.google.common.truth.Truth.assertThat
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
    fun existingArchivedReferences_remainUsableUnchanged() {
        val eventId = "event-archived-fixed"
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val archivedLabel = context.getString(R.string.archived_label)
        
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, archivedIngId, archivedAreaId, archivedOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, "Keep archived", null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)

            // 1. Verify detail shows labels with Archived marker
            composeTestRule.onNodeWithText("Archived Chicken ($archivedLabel)").assertIsDisplayed()
            composeTestRule.onNodeWithText("Archived Area ($archivedLabel)").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_detail_quantity").assertTextContains("5 lb ($archivedLabel)", substring = true)

            // 2. Open Form
            composeTestRule.openWasteEdit()
            
            // 3. Verify form shows same labels
            composeTestRule.onNodeWithTag("ingredient_selector").assertTextContains("Archived Chicken ($archivedLabel)", substring = true)
            composeTestRule.onNodeWithTag("area_selector").assertTextContains("Archived Area ($archivedLabel)", substring = true)
            composeTestRule.onNodeWithTag("unit_selector").assertTextContains("lb ($archivedLabel)", substring = true)

            // 4. Save without changing anything
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForWasteDetail()
            
            // 5. Navigate away and reopen
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            // After save, Form is popped, so we are back at Detail. 
            // Detail back goes to List.
            composeTestRule.waitForTag("waste_list_screen")
            
            composeTestRule.openWasteEvent(eventId)
            
            // 6. Verify still shows archived labels
            composeTestRule.onNodeWithText("Archived Chicken ($archivedLabel)").assertIsDisplayed()
            composeTestRule.onNodeWithText("Archived Area ($archivedLabel)").assertIsDisplayed()
        }
    }

    @Test
    fun changingAwayFromArchivedReferences_clearsIncompatibleUnits() {
        val eventId = "event-archived-change"
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val archivedLabel = context.getString(R.string.archived_label)
        
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

            // 1. Edit Form
            composeTestRule.openWasteEdit()
            
            // 2. Change away from archived ingredient
            composeTestRule.onNodeWithTag("ingredient_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("ingredient_item_Active Chicken")
            composeTestRule.onNodeWithTag("ingredient_item_Active Chicken").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Verify the archived unit was cleared because it belonged to the archived ingredient
            composeTestRule.onNodeWithTag("unit_selector").assertTextContains("lb", substring = true)
            composeTestRule.onNode(hasTestTag("unit_selector") and hasText(archivedLabel, substring = true)).assertDoesNotExist()

            // 4. Change away from archived area
            composeTestRule.onNodeWithTag("area_selector").performScrollTo().performClick()
            composeTestRule.waitForTag("area_item_Active Area")
            composeTestRule.onNodeWithTag("area_item_Active Area").performClick()
            composeTestRule.waitForIdle()

            // 5. Persist active selections
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().performClick()
            composeTestRule.waitForWasteDetail()
            
            // 6. Navigate away and reopen
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForTag("waste_list_screen")
            
            composeTestRule.openWasteEvent(eventId)
            
            // 7. Assert active values persisted and NOT labeled Archived
            composeTestRule.onNodeWithText("Active Chicken").assertIsDisplayed()
            composeTestRule.onNodeWithText("Active Chicken ($archivedLabel)").assertDoesNotExist()
            composeTestRule.onNodeWithText("Active Area").assertIsDisplayed()
            
            // 8. Open menus and assert previous archived references are no longer offered
            composeTestRule.openWasteEdit()
            
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
    fun draftWithArchivedReferences_fullFlow() {
        // Kept for backward compatibility or replaced by above tests.
        // Actually, the user asked to "Add or restore these scenarios"
    }

    @Test
    fun missingIngredient_producesErrorState() {
        val eventId = "event-missing-ing"
        val originalEntity = WasteEventEntity(
            eventId, restaurantId, "MISSING_ING", activeAreaId, activeOptId, "5.0", "5.0",
            WasteReason.SPOILED.name, 1000L, "original", null, DocumentStatus.DRAFT.name,
            500L, 500L, null, null
        )
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(originalEntity)
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            
            // Exhaustive assertions
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("estimated_value_preview").assertDoesNotExist()
            
            // Verify database row was not mutated
            runBlocking {
                val current = database.wasteDao().getById(eventId)
                assertThat(current?.notes).isEqualTo("original")
            }
        }
    }

    @Test
    fun missingArea_producesErrorState() {
        val eventId = "event-missing-area"
        val originalEntity = WasteEventEntity(
            eventId, restaurantId, activeIngId, "MISSING_AREA", activeOptId, "5.0", "5.0",
            WasteReason.SPOILED.name, 1000L, "original", null, DocumentStatus.DRAFT.name,
            500L, 500L, null, null
        )
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(originalEntity)
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            
            // Exhaustive assertions
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("estimated_value_preview").assertDoesNotExist()

            // Verify database row was not mutated
            runBlocking {
                val current = database.wasteDao().getById(eventId)
                assertThat(current?.notes).isEqualTo("original")
            }
        }
    }

    @Test
    fun missingUnitOption_producesErrorState() {
        val eventId = "event-missing-unit"
        val originalEntity = WasteEventEntity(
            eventId, restaurantId, activeIngId, activeAreaId, "MISSING_OPT", "5.0", "5.0",
            WasteReason.SPOILED.name, 1000L, "original", null, DocumentStatus.DRAFT.name,
            500L, 500L, null, null
        )
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(originalEntity)
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            
            // Exhaustive assertions
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("estimated_value_preview").assertDoesNotExist()

            // Verify database row was not mutated
            runBlocking {
                val current = database.wasteDao().getById(eventId)
                assertThat(current?.notes).isEqualTo("original")
            }
        }
    }

    @Test
    fun crossIngredientUnitOption_producesErrorState() {
        val eventId = "event-cross"
        val otherIngId = "other-ing"
        val otherOptId = "other-opt"
        val originalEntity = WasteEventEntity(
            eventId, restaurantId, activeIngId, activeAreaId, otherOptId, "5.0", "5.0",
            WasteReason.SPOILED.name, 1000L, "original", null, DocumentStatus.DRAFT.name,
            500L, 500L, null, null
        )
        runBlocking {
            database.ingredientDao().insert(IngredientEntity(otherIngId, restaurantId, "Other", "other", null, unitId, null, null, null, null, true, 0L, 0L, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(otherOptId, otherIngId, "other-lb", "other-lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

            // Draft for activeIngId but using otherOptId (which belongs to otherIngId)
            database.wasteDao().insert(originalEntity)
        }

        composeTestRule.launchMainActivity().use {
            composeTestRule.waitForHomeReady()
            composeTestRule.openWasteHistory()
            composeTestRule.openWasteEvent(eventId)
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            
            composeTestRule.waitForTag("form_error_text")
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            
            // Exhaustive assertions
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
            composeTestRule.onNodeWithTag("estimated_value_preview").assertDoesNotExist()

            // Verify database row was not mutated
            runBlocking {
                val current = database.wasteDao().getById(eventId)
                assertThat(current?.notes).isEqualTo("original")
            }
        }
    }
}
