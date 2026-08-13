package com.miara.cuentame.feature.counts.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.*
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
import com.miara.cuentame.test.ConfigurableAttachmentPermissionManager
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    @Inject
    lateinit var failureBoundary: com.miara.cuentame.core.database.repository.IntegrationFailureBoundary

    @Inject
    lateinit var attachmentPermissionManager: com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun setup() {
        hiltRule.inject()
        (failureBoundary as? com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary)?.reset()
        (attachmentPermissionManager as? ConfigurableAttachmentPermissionManager)?.shouldFail = false
        
        runBlocking {
            testStateManager.resetAll()

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
            preferencesRepository.setAppLocaleTag("en-US")
        }
    }

    @After
    fun teardown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun full_lifecycle_test() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Open Count tab
            composeTestRule.onNodeWithTag("nav_count").performClick()
            
            // 2. Start New Count (FAB)
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("start_count_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("start_count_fab").performClick()
            
            // 3. Enter count name
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("count_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_name_input").performTextReplacement("Monthly Count")
            
            // 4. Select areas
            composeTestRule.onNodeWithTag("area_checkbox_area_dry_life").performClick()
            composeTestRule.onNodeWithTag("area_checkbox_area_kitchen_life").performClick()
            
            // 5. Save (Button at bottom)
            composeTestRule.onNodeWithTag("start_count_button").performScrollTo().performClick()
            
            // 6. Wait for detail and Verify
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("count_detail_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("count_detail_name").assertTextEquals("Monthly Count")
            
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 8. Enter quantity 75 lb
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNode(hasSetTextAction() and SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("quantity_") && tag.contains("ing_chicken_life")
            }).performTextReplacement("75")
            
            // Wait for autosave
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 9. Navigate away and reopen
            composeTestRule.onNodeWithTag("count_back_button").performClick()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Dry Storage").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            
            // 10. Verify 75 persisted
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText("75") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasText("75") and hasSetTextAction()).assertIsDisplayed()
            
            // 11. Complete Dry Storage
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            if (composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            }
            
            // 12. Open Main Kitchen
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Main Kitchen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            
            // 13. Search and Add Chicken Breast
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_search").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_search").performTextReplacement("Chicken")
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText("Chicken Breast").onFirst().performClick()
            
            // 14. Enter 10 lb
            composeTestRule.waitUntil(60000) {
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
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithContentDescription("Saved").fetchSemanticsNodes().isNotEmpty()
            }

            // 15. Verify "Opening Balance"
            composeTestRule.onAllNodes(SemanticsMatcher("") {
                val tag = it.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith("historical_expected_") && tag.contains("ing_chicken")
            }).onFirst().assertTextContains("Opening Balance")
            
            // 16. Complete Main Kitchen
            composeTestRule.onNodeWithTag("complete_area_button").performClick()
            if (composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            }
            
            // 17. Open adjustment review
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("complete_count_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("complete_count_button").performClick()
            
            // 18. Verify review data exact values
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("confirm_completion_button").fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().size >= 2
            }
            
            // Dry Storage: 80 lb expected, 75 lb counted -> -5 lb adjustment
            val dryStorageRow = SemanticsMatcher("") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("review_line_") && tag.contains("Dry Storage")
                } ?: false
            }
            composeTestRule.onNode(hasText("Expected: 80", substring = true) and hasAnyAncestor(dryStorageRow)).assertExists()
            composeTestRule.onNode(hasText("75 lb") and hasAnyAncestor(dryStorageRow)).assertExists()
            composeTestRule.onNode(hasText("Adjustment: -5", substring = true) and hasAnyAncestor(dryStorageRow)).assertExists()

            // Main Kitchen: 0 lb expected (newly added), 10 lb counted -> +10 lb adjustment
            val kitchenRow = SemanticsMatcher("") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("review_line_") && tag.contains("Main Kitchen")
                } ?: false
            }
            composeTestRule.onNode(hasText("Opening Balance") and hasAnyAncestor(kitchenRow)).assertExists()
            composeTestRule.onNode(hasText("10 lb") and hasAnyAncestor(kitchenRow)).assertExists()
            composeTestRule.onNode(hasText("Adjustment: +10", substring = true) and hasAnyAncestor(kitchenRow)).assertExists()
            
            // 19. Complete count
            composeTestRule.onNodeWithTag("confirm_completion_button").performClick()
            if (composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            }
            
            // 20. Verify COMPLETED status
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty()
            }
            
            // 21. Open completed areas and verify read-only & snapshots
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNode(SemanticsMatcher("historical expected") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("historical_expected_") && tag.endsWith("_ing_chicken_life")
                } ?: false
            }).assertTextContains("Expected: 80", substring = true)

            composeTestRule.onNode(SemanticsMatcher("historical adjustment") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("historical_adjustment_") && tag.endsWith("_ing_chicken_life")
                } ?: false
            }).assertTextContains("Adjustment: -5", substring = true)
            
            composeTestRule.onNodeWithTag("ingredient_search").assertDoesNotExist()
            composeTestRule.onNodeWithTag("complete_area_button").assertDoesNotExist()
            
            composeTestRule.onNodeWithTag("count_back_button").performClick()

            // Verify Main Kitchen snapshots
            composeTestRule.onNodeWithText("Main Kitchen").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNode(SemanticsMatcher("historical expected kitchen") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("historical_expected_") && tag.endsWith("_ing_chicken_life")
                } ?: false
            }).assertTextContains("Opening Balance")

            composeTestRule.onNode(SemanticsMatcher("historical adjustment kitchen") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("historical_adjustment_") && tag.endsWith("_ing_chicken_life")
                } ?: false
            }).assertTextContains("Adjustment: +10", substring = true)

            composeTestRule.onNodeWithTag("count_back_button").performClick()


            // 22. Void count
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("void_count_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("void_count_button").performClick()
            if (composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            }
            
            // 23. Verify VOIDED status
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Voided", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            
            // 24. Snapshots after reopening VOIDED
            composeTestRule.onNodeWithText("Dry Storage").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(SemanticsMatcher("") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("historical_expected_") == true
                }).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNode(SemanticsMatcher("historical expected voided") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    tag.startsWith("historical_expected_") && tag.endsWith("_ing_chicken_life")
                } ?: false
            }).assertTextContains("Expected: 80", substring = true)
            
            composeTestRule.onNodeWithTag("count_back_button").performClick()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("home_dashboard_list").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
