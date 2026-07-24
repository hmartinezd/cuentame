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
class WasteFailureUiTest {

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

    private val restaurantId = "rest-fail"
    private val areaId = "area-fail"
    private val ingId = "ing-fail"
    private val unitId = "unit-fail"
    private val optId = "opt-fail"

    @Before
    fun setup() {
        hiltRule.inject()
        seedData()
    }

    private fun seedData() = runBlocking {
        database.clearAllTables()
        preferencesRepository.setOnboardingCompleted(true)
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Fail Rest", "USD", "en", 0L, 0L, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Fail Area", "fail area", 1, true, 0L, 0L, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Fail Ingredient", "fail ingredient", null, unitId, null, null, null, null, true, 0L, 0L, null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun postFailure_keepsDialogAndShowsError() {
        val boundary = failureBoundary as com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
        boundary.failurePoint = "post-after-movement"

        // Seed a DRAFT
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                "event-fail", restaurantId, ingId, areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

            // Wait for loading to finish
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
            }
            
            // Wait for Home screen to load
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("waste_item_event-fail").performClick()
            composeTestRule.waitForIdle()

            // Try to Post
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            composeTestRule.waitForIdle()

            // Verify dialog remains open (check for title inside dialog)
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.post_waste)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify snackbar/error appears
            composeTestRule.onNodeWithText(context.getString(R.string.error_generic)).assertIsDisplayed()

            // Clean up
            boundary.failurePoint = null
        }
    }

    @Test
    fun voidFailure_keepsDialogAndShowsError() {
        val boundary = failureBoundary as com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
        boundary.failurePoint = "void-after-reversal"

        // Seed a POSTED
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                "event-void-fail", restaurantId, ingId, areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.POSTED.name,
                500L, 500L, 600L, null
            ))
            // Seed matching movement
            database.inventoryMovementDao().insert(com.miara.cuentame.core.database.entity.InventoryMovementEntity(
                "mov-1", restaurantId, ingId, areaId, "WASTE", "-5.0", null, null,
                1000L, "WASTE_EVENT", "event-void-fail", "event-void-fail", "waste-post:event-void-fail", null, 600L
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

            // Wait for loading to finish
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
            }
            
            // Wait for Home screen to load
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()

            // Wait for list to have items
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("waste_item_event-void-fail").performClick()
            composeTestRule.waitForIdle()

            // Try to Void
            composeTestRule.onNodeWithTag("waste_void_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            composeTestRule.waitForIdle()

            // Verify dialog remains open
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.void_waste)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(context.getString(R.string.error_generic)).assertIsDisplayed()

            boundary.failurePoint = null
        }
    }
}
