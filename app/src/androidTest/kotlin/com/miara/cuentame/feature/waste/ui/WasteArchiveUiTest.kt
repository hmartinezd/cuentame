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
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Archive Rest", "USD", "en", 0L, 0L, null))
        
        database.inventoryAreaDao().upsert(InventoryAreaEntity(archivedAreaId, restaurantId, "Archived Area", "archived area", 1, false, 0L, 0L, 100L))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(activeAreaId, restaurantId, "Active Area", "active area", 2, true, 0L, 0L, null))
        
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        
        database.ingredientDao().insert(IngredientEntity(archivedIngId, restaurantId, "Archived Chicken", "archived chicken", null, unitId, null, null, null, null, false, 0L, 0L, 100L))
        database.ingredientDao().insert(IngredientEntity(activeIngId, restaurantId, "Active Chicken", "active chicken", null, unitId, null, null, null, null, true, 0L, 0L, null))
        
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(archivedOptId, archivedIngId, "lb", "lb", null, BigDecimal.ONE, true, true, true, false, 0L, 0L, 100L))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(activeOptId, activeIngId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun debug_checkFormState() {
        val eventId = "event-debug"
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, archivedIngId, archivedAreaId, archivedOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        waitForHome()
        composeTestRule.onNodeWithTag("view_waste_button").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("Archived Chicken", substring = true) and hasClickAction()).onFirst().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("waste_edit_button").performClick()
        composeTestRule.waitForIdle()
        
        // Wait a bit and check what's on screen
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        
        // Use a loop to keep it alive for a few seconds so I can screenshot
        repeat(5) {
            composeTestRule.mainClock.advanceTimeBy(1000)
            composeTestRule.waitForIdle()
        }
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

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            // Click item in list
            composeTestRule.onAllNodes(hasText("Archived Chicken", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()

            // 1. Verify detail shows labels
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertIsDisplayed()

            // 2. Edit Form
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            // Verify form fields
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 3. Change away from archived ingredient
            composeTestRule.onNodeWithTag("ingredient_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("Active Chicken", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Active Chicken", substring = true)).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            // 4. Change away from archived area
            composeTestRule.onNodeWithTag("area_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("Active Area", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Active Area", substring = true)).onFirst().performClick()
            composeTestRule.waitForIdle()

            // 5. Persist active selections after leaving archived references
            composeTestRule.onNodeWithTag("waste_save_button").performScrollTo().assertIsEnabled()
            composeTestRule.onNodeWithTag("waste_save_button").performClick()
            composeTestRule.waitForIdle()
            
            // Should be back at WasteDetail
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            
            // Go back to List
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.waitForIdle()

            // Navigate back to history
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            
            // Reopen the draft
            composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            // Assert active values persisted and NOT labeled Archived
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Active Chicken").assertIsDisplayed()
            composeTestRule.onNodeWithText("Active Area").assertIsDisplayed()
            
            // Open menus and assert previous archived references are no longer offered
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("ingredient_selector").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertDoesNotExist()
            
            // Close menu by clicking outside or hitting back (simulating by clicking selector again if supported, or just verify)
            // Actually, we can just check area selector too
            composeTestRule.onNodeWithTag("area_selector").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Archived Area", substring = true).assertDoesNotExist()
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

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            val errorLabel = context.getString(R.string.error_ingredient_not_found)
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText(errorLabel, substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText(errorLabel, substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("form_error_text").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ingredient_selector").assertDoesNotExist()
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

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("form_error_text").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("area_selector").assertDoesNotExist()
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

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("form_error_text").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("unit_selector").assertDoesNotExist()
        }
    }

    @Test
    fun crossIngredientUnitOption_producesErrorState() {
        val otherIngId = "other-ing"
        val otherOptId = "other-opt"
        runBlocking {
            database.ingredientDao().insert(IngredientEntity(otherIngId, restaurantId, "Other", "other", null, unitId, null, null, null, null, true, 0L, 0L, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(otherOptId, otherIngId, "other-lb", "other-lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

            // Draft for activeIngId but using otherOptId (which belongs to otherIngId)
            database.wasteDao().insert(WasteEventEntity(
                "event-cross", restaurantId, activeIngId, activeAreaId, otherOptId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Active Chicken", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("waste_edit_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("form_error_text").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithText("Archive Rest").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
