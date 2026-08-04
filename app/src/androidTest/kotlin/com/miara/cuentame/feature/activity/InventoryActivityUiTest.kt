package com.miara.cuentame.feature.activity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.feature.activity.logic.AndroidInventoryActivityTextResolver
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InventoryActivityUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject lateinit var testStateManager: TestStateManager
    @Inject lateinit var restaurantRepository: RestaurantRepository
    @Inject lateinit var ingredientRepository: IngredientRepository
    @Inject lateinit var areaRepository: InventoryAreaRepository
    @Inject lateinit var purchaseRepository: PurchaseRepository
    @Inject lateinit var wasteRepository: WasteRepository
    @Inject lateinit var stockCountRepository: StockCountRepository
    @Inject lateinit var productionBatchRepository: ProductionBatchRepository
    @Inject lateinit var preparationRecipeRepository: PreparationRecipeRepository
    @Inject lateinit var activityRepository: InventoryActivityRepository
    @Inject lateinit var movementDao: InventoryMovementDao

    private lateinit var fixture: CanonicalInventoryActivityFixture

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.seedBaseline()
            fixture = seedCanonicalInventoryActivity(
                restaurantRepository, ingredientRepository, areaRepository,
                purchaseRepository, wasteRepository, stockCountRepository,
                productionBatchRepository, preparationRecipeRepository, 
                activityRepository, movementDao
            )
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun activityFullFlow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = AndroidInventoryActivityTextResolver(context)
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Activity
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 2. Verify rows by exact movement tags
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.stockCountMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionOutputMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.originalMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}").assertIsDisplayed()

            // 3. Verify Summary (exact counts from fixture)
            composeTestRule.onNodeWithTag("inventory_activity_summary").assertIsDisplayed()
            // fixture adds exactly 7 movements
            composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextContains("7", substring = true)

            // 4. Search by localized Purchase source text
            val purchaseTitle = resolver.sourceTitle(InventoryActivitySourceInfo.Purchase(null, "INV-001", true))
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput(purchaseTitle)
            
            // Wait for debounced search
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_${fixture.purchaseMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Only matching row remains
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}").assertDoesNotExist()

            // 5. Active search indicator visible
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertIsDisplayed()

            // 6. Reset search via chip
            composeTestRule.onNodeWithTag("inventory_activity_active_search").onChildren().filterToOne(hasContentDescription("Remove")).performClick()
            
            // 7. Verify search field is exactly empty
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("")
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertDoesNotExist()

            // 7.5. Re-search to reach filtered-empty state
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("NONEXISTENT_ACTIVITY")
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_filtered_empty")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty_reset").performClick()
            
            // Verify reset from filtered-empty also works
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("")
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            
            // 8. Open Production consumption row -> Production Batch source
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("production_batch_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            
            // 9. Reversal -> Original -> Navigation
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.reversalMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.originalMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_open_reversal").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_open_original").assertDoesNotExist()
            
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.reversalMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }
}
