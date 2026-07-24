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
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("waste_item_$eventId").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_item_$eventId").performClick()

            // 1. Verify detail shows labels with (Archived)
            val archivedSuffix = context.getString(R.string.archived_label)
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Archived Chicken", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assert(hasText(archivedSuffix, substring = true))
            composeTestRule.onNodeWithText("Archived Area", substring = true).assert(hasText(archivedSuffix, substring = true))
            composeTestRule.onNodeWithText("5.0 lb", substring = true).assert(hasText(archivedSuffix, substring = true))

            // 2. Edit Form
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
            
            // Verify form fields show (Archived)
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("ingredient_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_selector").assert(hasText("Archived Chicken", substring = true)).assert(hasText(archivedSuffix, substring = true))
            composeTestRule.onNodeWithTag("area_selector").assert(hasText("Archived Area", substring = true)).assert(hasText(archivedSuffix, substring = true))
            composeTestRule.onNodeWithTag("unit_selector").assert(hasText("lb", substring = true)).assert(hasText(archivedSuffix, substring = true))

            // 3. Change away from archived ingredient
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_Active Chicken").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_Active Chicken").performClick()
            
            // Verify unit cleared and replaced by active unit
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodes(hasTestTag("unit_selector") and hasText("lb", substring = true) and hasText(archivedSuffix, substring = true).not()).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify archived ingredient no longer in menu
            composeTestRule.onNodeWithTag("ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Archived Chicken").assertDoesNotExist()
            
            // 4. Change away from archived area
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("area_item_Active Area").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("area_item_Active Area").performClick()
            
            // Verify archived area removed from menu
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onNodeWithTag("area_item_Archived Area").assertDoesNotExist()
        }
    }

    @Test
    fun missingReferences_produceErrorState() {
        val testCases = listOf(
            "MISSING_ING" to archivedAreaId,
            archivedIngId to "MISSING_AREA"
        )
        
        testCases.forEach { (ing, area) ->
            val eventId = "event-missing-${ing.take(3)}-${area.take(3)}"
            runBlocking {
                database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
                database.wasteDao().insert(WasteEventEntity(
                    eventId, restaurantId, ing, area, archivedOptId, "5.0", "5.0",
                    WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                    500L, 500L, null, null
                ))
                database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
            }

            ActivityScenario.launch(MainActivity::class.java).use {
                val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
                waitForHome()
                composeTestRule.onNodeWithTag("view_waste_button").performClick()
                
                composeTestRule.waitUntil(30000) {
                    composeTestRule.onAllNodesWithTag("waste_item_$eventId").fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithTag("waste_item_$eventId").performClick()
                
                composeTestRule.waitUntil(30000) {
                    composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.action_edit)).fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
                
                composeTestRule.waitUntil(30000) {
                    composeTestRule.onAllNodesWithText(context.getString(R.string.state_error_desc), substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithTag("waste_save_button").assertDoesNotExist()
            }
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
