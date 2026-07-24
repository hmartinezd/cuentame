package com.miara.cuentame.feature.counts.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

@OptIn(ExperimentalTestApi::class)
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
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
            preferencesRepository.clearOnboardingDraft()

            val now = Instant.now()
            database.restaurantDao().insert(Restaurant(RestaurantId("rest_lifecycle"), "Test Lifecycle Rest", "USD", "en-US", now, now, null).toEntity())
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_dry_life"), RestaurantId("rest_lifecycle"), "Dry Storage", "dry storage", 0, true, now, now, null).toEntity()
            )
            database.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_kitchen_life"), RestaurantId("rest_lifecycle"), "Main Kitchen", "main kitchen", 1, true, now, now, null).toEntity()
            )
            
            val ingId = IngredientId("ing_chicken_life")
            database.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_lifecycle"), "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), InventoryAreaId("area_dry_life"), null, null, null, true, now, now, null).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb_life"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, now, now, null).toEntity()
            )
            database.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_case_life"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, now, now, null).toEntity()
            )

            database.inventoryMovementDao().insert(
                InventoryMovementEntity(
                    id = "mov_life_1",
                    restaurantId = "rest_lifecycle",
                    ingredientId = "ing_chicken_life",
                    areaId = "area_dry_life",
                    movementType = InventoryMovementType.PURCHASE.name,
                    quantityBaseSigned = "80",
                    unitCostBaseSnapshot = "2",
                    totalValueSnapshot = "160",
                    effectiveAt = now.minusSeconds(3600).toEpochMilli(),
                    sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                    sourceDocumentId = "pur_life_1",
                    sourceOperationId = "op_life_1",
                    sourceLineId = "line_life_1",
                    reversalOfMovementId = null,
                    createdAt = now.minusSeconds(3600).toEpochMilli()
                )
            )

            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
        }
    }

    @Test
    fun full_lifecycle_test() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Open Count tab
            composeTestRule.onNodeWithTag("nav_count").performClick()
            
            // 2. Start New Count (FAB)
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("start_count_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("start_count_fab").performClick()
            
            // 3. Enter count name
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("count_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_name_input").performTextReplacement("Monthly Count")
            
            // 4. Select areas
            composeTestRule.onNodeWithTag("area_checkbox_area_dry_life").performClick()
            composeTestRule.onNodeWithTag("area_checkbox_area_kitchen_life").performClick()
            
            // 5. Save (Button at bottom)
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            
            // 6. Wait for detail and Verify
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_detail_name").assertTextEquals("Monthly Count")
            
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 8. Enter quantity 75 lb
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNode(hasSetTextAction() and SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("quantity_") && tag.contains("ing_chicken_life")
            }).performTextReplacement("75")
            
            // Wait for autosave
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 9. Navigate away and reopen
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Dry Storage").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 10. Verify 75 persisted
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasText("75") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasText("75") and hasSetTextAction()).assertIsDisplayed()
            
            // 11. Complete Dry Storage
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            
            // 12. Open Main Kitchen
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            
            // 13. Search and Add Chicken Breast
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("ingredient_search").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_search").performTextReplacement("Chicken")
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText("Chicken Breast").onFirst().performClick()
            
            // 14. Enter 10 lb
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasSetTextAction() and SemanticsMatcher("") {
                    val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                    tag != null && tag.startsWith("quantity_") && tag.contains("ing_chicken_life")
                }).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasSetTextAction() and SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("quantity_") && tag.contains("ing_chicken_life")
            }).performTextReplacement("10")
            
            // Wait for autosave
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }

            // 15. Verify "Opening Balance"
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_expected_") && tag.contains("ing_chicken")
            }).onFirst().assertTextContains("Opening Balance")
            
            // 16. Complete Main Kitchen
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            
            // 17. Open adjustment review
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("complete_count_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("complete_count_button").performClick()
            
            // 18. Verify review data exact values
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Adjustment Review").fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().size >= 2
            }
            
            composeTestRule.onNode(hasText("Expected: 80 lb") and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("review_line_") && tag.contains("Dry Storage")
            })).assertIsDisplayed()
            
            // 19. Complete count
            composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
            
            // 20. Verify COMPLETED status
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 21. Open completed areas and verify read-only
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
            
            composeTestRule.onNodeWithTag("count_back_button").performClick()

            // 22. Void count
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodesWithTag("void_count_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("void_count_button").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            
            // 23. Verify VOIDED status
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Voided").fetchSemanticsNodes().isNotEmpty()
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
