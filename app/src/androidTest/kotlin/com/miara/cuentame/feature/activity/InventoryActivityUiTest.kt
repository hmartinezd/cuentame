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
    @Inject lateinit var ingredientRepository: IngredientRepository
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
                ingredientRepository,
                purchaseRepository,
                wasteRepository,
                stockCountRepository,
                productionBatchRepository,
                preparationRecipeRepository,
                activityRepository,
                movementDao
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

            // 2. Verify rows by exact movement tags with scrolling
            val rows = listOf(
                fixture.purchaseMovementId,
                fixture.wasteMovementId,
                fixture.stockCountMovementId,
                fixture.productionConsumptionMovementId,
                fixture.productionOutputMovementId,
                fixture.originalMovementId,
                fixture.reversalMovementId
            )
            
            rows.forEach { movementId ->
                composeTestRule.onNodeWithTag("inventory_activity_row_${movementId.value}")
                    .performScrollTo()
                    .assertIsDisplayed()
            }

            // 3. Verify Summary (exact counts from fixture)
            composeTestRule.onNodeWithTag("inventory_activity_summary").assertIsDisplayed()
            // fixture adds exactly 7 movements
            composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextContains("7", substring = true)
            
            // Re-assert specific directions if known
            // 1 Purchase (+), 1 Waste (-), 1 Stock Count (+6), 1 Production consumption (-1), 1 Production output (+1), 
            // 1 Voided Purchase (+5), 1 Reversal (-5)
            // Incoming: 1, 1, 1, 1 = 4? No. 
            // 10.0 (P), -1.0 (W), +6.0 (SC), -1.0 (PC), +1.0 (PO), +5.0 (VP), -5.0 (R)
            // Signs > 0: Purchase, SC, PO, VP = 4
            // Signs < 0: Waste, PC, R = 3
            composeTestRule.onNodeWithTag("inventory_activity_incoming_count").assertTextContains("4", substring = true)
            composeTestRule.onNodeWithTag("inventory_activity_outgoing_count").assertTextContains("3", substring = true)
            composeTestRule.onNodeWithTag("inventory_activity_reversal_count").assertTextContains("1", substring = true)

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
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}")
                .performScrollTo()
                .assertIsDisplayed()
            
            // 8. Open Production consumption row -> Production Batch source
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("production_batch_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            // Back from Source Document -> Activity Detail
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}").assertIsDisplayed()
            
            // Back from Activity Detail -> Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            
            // 9. Reversal -> Original -> Navigation
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.reversalMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.originalMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_open_reversal").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_open_original").assertDoesNotExist()
            
            // Press Back once from related Detail -> Activity List (due to popUpTo inclusive)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }
}
