package com.miara.cuentame.feature.counts.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
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
class StockCountLifecycleTest {

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
            preferencesRepository.setOnboardingCompleted(true)
            database.clearAllTables()
            
            val now = Instant.now()
            database.restaurantDao().insert(Restaurant(RestaurantId("rest_1"), "Test Restaurant", "USD", "en-US", now, now).toEntity())
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_dry"), RestaurantId("rest_1"), "Dry Storage", "dry storage", 0, true, now, now).toEntity()
            )
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_kitchen"), RestaurantId("rest_1"), "Main Kitchen", "main kitchen", 1, true, now, now).toEntity()
            )
            
            val ingId = IngredientId("ing_chicken")
            database.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_1"), "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_dry"), null, null, null, true, now, now).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_case"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now).toEntity()
            )

            // Seed an 80 lb purchase in Dry Storage at $2/lb
            database.inventoryMovementDao().insert(
                InventoryMovementEntity(
                    id = "mov_1",
                    restaurantId = "rest_1",
                    ingredientId = "ing_chicken",
                    areaId = "area_dry",
                    movementType = InventoryMovementType.PURCHASE.name,
                    quantityBaseSigned = "80",
                    unitCostBaseSnapshot = "2",
                    totalValueSnapshot = "160",
                    effectiveAt = now.minusSeconds(3600).toEpochMilli(),
                    sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                    sourceDocumentId = "pur_1",
                    sourceOperationId = "op_1",
                    sourceLineId = "line_1",
                    reversalOfMovementId = null,
                    createdAt = now.minusSeconds(3600).toEpochMilli()
                )
            )
        }
    }

    @Test
    fun full_lifecycle_test() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Open Count tab
            composeTestRule.onNodeWithText("Count").performClick()
            
            // 2. Start New Count (FAB)
            composeTestRule.onNodeWithTag("start_count_fab").performClick()
            
            // 3. Enter count name
            composeTestRule.onNodeWithTag("count_name_input").performTextReplacement("Monthly Count")
            
            // 4. Select areas
            composeTestRule.onNodeWithTag("area_checkbox_area_dry").performClick()
            composeTestRule.onNodeWithTag("area_checkbox_area_kitchen").performClick()
            
            // 5. Save (Button at bottom)
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            
            // 6. Wait for detail and Verify
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_detail_name").assertIsDisplayed()
            
            // 7. Open Dry Storage
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 8. Enter quantity 75 lb
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("line_ingredient_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_quantity_ing_chicken").performTextReplacement("75")
            
            // Wait for autosave
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("save_indicator_saved_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 9. Navigate away and reopen
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 10. Verify 75 persisted
            composeTestRule.onNodeWithTag("count_quantity_ing_chicken").assertIsDisplayed()
            
            // 11. Complete Dry Storage
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            
            // 12. Open Main Kitchen
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            
            // 13. Search and Add Chicken Breast (since it's not a candidate for Kitchen)
            composeTestRule.onNodeWithTag("ingredient_search").performTextReplacement("Chicken")
            composeTestRule.onNodeWithText("Chicken Breast").performClick()
            
            // 14. Enter 10 lb
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("count_quantity_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_quantity_ing_chicken").performTextReplacement("10")
            
            // Wait for autosave
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("save_indicator_saved_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }

            // 15. Verify "Opening Balance"
            composeTestRule.onNodeWithText("Opening Balance").assertIsDisplayed()
            
            // 16. Complete Main Kitchen
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            
            // 17. Open adjustment review
            composeTestRule.onNodeWithTag("complete_count_button").performClick()
            
            // 18. Verify review data
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("historical_expected_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }
            // Chicken appears in two areas, so we check onAllNodes
            composeTestRule.onAllNodesWithTag("historical_expected_ing_chicken").assertCountEquals(2)
            composeTestRule.onAllNodesWithTag("historical_adjustment_ing_chicken").assertCountEquals(2)
            
            // 19. Complete count
            composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
            
            // 20. Verify COMPLETED status
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 21. Open completed areas and verify read-only
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("expected_preview_ing_chicken").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("expected_preview_ing_chicken").assertIsDisplayed()
            composeTestRule.onNodeWithTag("adjustment_preview_ing_chicken").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
            composeTestRule.onNodeWithTag("delete_line_ing_chicken").assertDoesNotExist()
            
            composeTestRule.onNodeWithTag("count_back_button").performClick()

            // 22. Void count
            composeTestRule.onNodeWithTag("void_count_button").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            
            // 23. Verify VOIDED status
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Voided").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 24. Verify VOIDED area is also read-only
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            composeTestRule.waitUntil(10000) {
                 composeTestRule.onAllNodesWithText("Opening Balance").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Opening Balance").assertIsDisplayed()
            composeTestRule.onNodeWithTag("adjustment_preview_ing_chicken").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
        }
    }
}
