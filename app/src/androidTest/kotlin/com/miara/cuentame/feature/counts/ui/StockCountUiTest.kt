package com.miara.cuentame.feature.counts.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
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

@HiltAndroidTest
class StockCountUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun setup() {
        hiltRule.inject()
        
        runBlocking {
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
            preferencesRepository.clearOnboardingDraft()
            
            val now = Instant.now()
            database.restaurantDao().insert(Restaurant(RestaurantId("rest_ui_test"), "Test UI Rest", "USD", "en-US", now, now, null).toEntity())
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_dry_ui"), RestaurantId("rest_ui_test"), "Dry Storage", "dry storage", 0, true, now, now, null).toEntity()
            )
            
            val ingId = IngredientId("ing_chicken_ui")
            database.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_ui_test"), "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_dry_ui"), null, null, null, true, now, now, null).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb_ui"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity()
            )
            
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
            preferencesRepository.clearOnboardingDraft()
        }
    }

    @Test
    fun start_count_flow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Open Count tab
            composeTestRule.onNodeWithTag("nav_count").performClick()
            composeTestRule.waitForIdle()
            
            // 2. Start New Count (FAB)
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("start_count_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("start_count_fab").performClick()
            composeTestRule.waitForIdle()
            
            // 3. Enter count name
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("count_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_name_input").performTextReplacement("Monthly Count")
            
            // 4. Select area (click the checkbox)
            composeTestRule.onNodeWithTag("area_checkbox_area_dry_ui").performClick()
            
            // 5. Save (Button at bottom)
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            composeTestRule.waitForIdle()
            
            // 6. Wait for detail and Verify
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_detail_name").assertIsDisplayed()
            composeTestRule.onNodeWithText("Dry Storage").assertIsDisplayed()
            
            // 7. Open area counting
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitForIdle()
            
            // 8. Enter quantity
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("line_item_") && tag.contains("ing_chicken")
            })).performTextReplacement("10")
            
            // Wait for autosave
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 9. Complete area
            composeTestRule.onNodeWithText("Complete Area").performClick()
            composeTestRule.waitForIdle()
            
            // 10. Verify area status in detail
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Completed", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
            
            // 11. Complete count (Opens Review)
            composeTestRule.onNodeWithTag("complete_count_button").performClick()
            composeTestRule.waitForIdle()
            
            // 12. Confirm completion (In Review Sheet)
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("confirm_completion_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
            composeTestRule.waitForIdle()
            
            // 13. Verify COMPLETED status
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Completed", substring = true).fetchSemanticsNodes().size >= 2
            }
            composeTestRule.onAllNodesWithText("Completed", substring = true).onFirst().assertIsDisplayed()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
