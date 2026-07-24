package com.miara.cuentame.feature.counts.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun setup() {
        hiltRule.inject()
        
        runBlocking {
            preferencesRepository.setOnboardingCompleted(true)
            database.clearAllTables()
            
            val now = Instant.now()
            database.restaurantDao().insert(Restaurant(RestaurantId("rest_1"), "Test Restaurant", "USD", "en-US", now, now, null).toEntity())
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_dry"), RestaurantId("rest_1"), "Dry Storage", "dry storage", 0, true, now, now, null).toEntity()
            )
            
            val ingId = IngredientId("ing_chicken")
            database.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_1"), "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_dry"), null, null, null, true, now, now, null).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity()
            )
        }
    }

    @Test
    fun start_count_flow() {
        // 1. Open Count tab
        composeTestRule.onNodeWithText("Count").performClick()
        
        // 2. Start New Count (FAB)
        composeTestRule.onNodeWithTag("start_count_fab").performClick()
        
        // 3. Enter count name
        composeTestRule.onNodeWithTag("count_name_input", useUnmergedTree = true).performTextReplacement("Monthly Count")
        
        // 4. Select area (click the checkbox)
        composeTestRule.onNodeWithTag("area_checkbox_area_dry", useUnmergedTree = true).performClick()
        
        // 5. Save (Button at bottom)
        composeTestRule.onNodeWithTag("start_count_button").performClick()
        
        // 6. Wait for detail and Verify
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("count_detail_name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dry Storage").assertIsDisplayed()
        
        // 7. Open area counting
        composeTestRule.onNodeWithText("Dry Storage").performClick()
        
        // 8. Enter quantity
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("line_ingredient_ing_chicken").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("count_quantity_ing_chicken", useUnmergedTree = true).performTextReplacement("10")
        
        // Wait for autosave
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("save_indicator_saved_ing_chicken").fetchSemanticsNodes().isNotEmpty()
        }
        
        // 9. Complete area
        composeTestRule.onNodeWithText("Complete Area").performClick()
        
        // 10. Verify area status in detail
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Area Completed").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Area Completed").assertIsDisplayed()
        
        // 11. Complete count (Opens Review)
        composeTestRule.onNodeWithTag("complete_count_button").performClick()
        
        // 12. Confirm completion (In Review Sheet)
        composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
        
        // 13. Verify COMPLETED status
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
    }
}
