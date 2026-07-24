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
            // 1. Clear everything
            database.clearAllTables()
            preferencesRepository.setOnboardingCompleted(false)
            preferencesRepository.clearOnboardingDraft()

            val now = Instant.now()
            // 2. Seed DB
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

            // Seed an 80 lb purchase in Dry Storage at $2/lb
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

            // 3. Set onboarding completed LAST
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun full_lifecycle_test() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()
            
            // Wait for loading to finish
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
            }

            // Wait for Home screen to load
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("nav_home", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Count tab
            composeTestRule.onNodeWithTag("nav_count").performClick()
            
            // 2. Start New Count (FAB)
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithTag("start_count_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("start_count_fab").performClick()
            
            // 3. Enter count name
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("count_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_name_input").performTextReplacement("Monthly Count")
            
            // 4. Select areas
            composeTestRule.onNodeWithTag("area_checkbox_area_dry_life").performClick()
            composeTestRule.onNodeWithTag("area_checkbox_area_kitchen_life").performClick()
            
            // 5. Save (Button at bottom)
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            
            // 6. Wait for detail and Verify
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_detail_name").assertTextEquals("Monthly Count")
            
            // 7. Get area ID for Dry Storage from UI or known
            // In setup we used area_dry and area_kitchen. StockCountArea IDs are generated.
            // We'll need to find the node and get its tag or just wait for it.
            
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 8. Enter quantity 75 lb
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            
            // Find quantity field for chicken in Dry Storage
            composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("line_item_") && tag.contains("ing_chicken")
            })).performTextReplacement("75")
            
            // Wait for autosave
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 9. Navigate away and reopen
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 10. Verify 75 persisted
            composeTestRule.onNode(hasText("75") and hasSetTextAction()).assertIsDisplayed()
            
            // 11. Complete Dry Storage
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            
            // 12. Open Main Kitchen
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            
            // 13. Search and Add Chicken Breast (since it's not a candidate for Kitchen)
            composeTestRule.onNodeWithTag("ingredient_search").performTextReplacement("Chicken")
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText("Chicken Breast").onFirst().performClick()
            
            // 14. Enter 10 lb
            composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("line_item_") && tag.contains("ing_chicken")
            })).performTextReplacement("10")
            
            // Wait for autosave
            composeTestRule.waitUntil(15000) {
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
            composeTestRule.onNodeWithTag("complete_count_button").performClick()
            
            // 18. Verify review data exact values
            composeTestRule.waitUntil(20000) {
                // Wait for the sheet title
                composeTestRule.onAllNodesWithText("Adjustment Review").fetchSemanticsNodes().isNotEmpty()
            }
            
            // Wait for content to load
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().size >= 2
            }
            
            // Dry Storage: 75 entered, 80 expected -> -5 adjustment
            composeTestRule.onNode(hasText("Expected: 80 lb") and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("review_line_") && tag.contains("Dry Storage")
            })).assertIsDisplayed()
            composeTestRule.onNode(hasText("Adjustment: -5 lb") and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("review_line_") && tag.contains("Dry Storage")
            })).assertIsDisplayed()
            
            // Main Kitchen: 10 entered, Opening Balance -> +10 adjustment
            composeTestRule.onNode(hasText("Opening Balance") and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("review_line_") && tag.contains("Main Kitchen")
            })).assertIsDisplayed()
            composeTestRule.onNode(hasText("Adjustment: +10 lb") and hasAnyAncestor(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("review_line_") && tag.contains("Main Kitchen")
            })).assertIsDisplayed()

            // 19. Complete count
            composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
            
            // 20. Verify COMPLETED status
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 21. Open completed areas and verify read-only persisted snapshots
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_expected_") && tag.contains("ing_chicken")
            }).onFirst().assertTextEquals("Expected: 80 lb")
            
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_adjustment_") && tag.contains("ing_chicken")
            }).onFirst().assertTextEquals("Adjustment: -5 lb")

            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
            
            // Verify mutation controls absent
            composeTestRule.onAllNodesWithContentDescription("Remove").assertCountEquals(0)
            composeTestRule.onNodeWithTag("complete_area_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("reopen_area_button").assertDoesNotExist()
            
            composeTestRule.onNodeWithTag("count_back_button").performClick()

            // 22. Void count
            composeTestRule.onNodeWithTag("void_count_button").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            
            // 23. Verify VOIDED status
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Voided").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 24. Verify VOIDED area is also read-only and has snapshots
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_expected_") && tag.contains("ing_chicken")
            }).onFirst().assertTextEquals("Opening Balance")
            
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_adjustment_") && tag.contains("ing_chicken")
            }).onFirst().assertTextEquals("Adjustment: +10 lb")
            
            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
            
            // Navigate away and reopen to verify persistence
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Monthly Count").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Monthly Count").performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Voided").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Voided").assertIsDisplayed()
            composeTestRule.onNode(hasText("Main Kitchen") and hasClickAction()).performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithText("Opening Balance").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Opening Balance").assertIsDisplayed()
        }
    }
}
