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
import java.time.Instant
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
    private val areaId = "area-archived"
    private val ingId = "ing-archived"
    private val unitId = "unit-archived"
    private val optId = "opt-archived"

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
        
        database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Archived Area", "archived area", 1, false, 0L, 0L, Instant.now().toEpochMilli()))
        database.inventoryAreaDao().upsert(InventoryAreaEntity("area-active", restaurantId, "Active Area", "active area", 2, true, 0L, 0L, null))
        
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Archived Chicken", "archived chicken", null, unitId, null, null, null, null, false, 0L, 0L, Instant.now().toEpochMilli()))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, false, 0L, 0L, Instant.now().toEpochMilli()))
        
        database.wasteDao().insert(WasteEventEntity(
            "event-archived", restaurantId, ingId, areaId, optId, "5.0", "5.0",
            WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
            500L, 500L, null, null
        ))
    }

    @Test
    fun draftWithArchivedReferences_labelsVisibleAndSelectable() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("waste_item_event-archived").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Archived Chicken", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Archived Area", substring = true).assertIsDisplayed()

            // Edit
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Archived Chicken", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Archived Area", substring = true).assertIsDisplayed()
            
            // Save unchanged
            composeTestRule.onNodeWithTag("waste_save_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Archived Chicken", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Archived Chicken", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun missingReference_showsError() {
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            database.wasteDao().insert(WasteEventEntity(
                "event-missing", restaurantId, "MISSING_ING", areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("waste_item_event-missing").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.action_edit)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.state_error_desc), substring = true).fetchSemanticsNodes().isNotEmpty()
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
