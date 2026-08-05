package com.miara.cuentame.feature.activity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.domain.repository.*
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
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

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
                scrollToActivityRow(movementId)
            }

            // 3. Verify Summary (exact counts from fixture)
            composeTestRule.onNodeWithTag("inventory_activity_list").performScrollToNode(hasTestTag("inventory_activity_summary"))
            composeTestRule.onNodeWithTag("inventory_activity_summary").assertIsDisplayed()
            
            // fixture adds exactly 7 movements
            composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextEquals("7")
            composeTestRule.onNodeWithTag("inventory_activity_incoming_count").assertTextEquals("4")
            composeTestRule.onNodeWithTag("inventory_activity_outgoing_count").assertTextEquals("3")
            composeTestRule.onNodeWithTag("inventory_activity_reversal_count").assertTextEquals("1")

            // 4. Search by unique Invoice Number
            val searchQuery = "INV-001"
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput(searchQuery)
            
            // Wait for debounced search
            composeTestRule.waitUntil(5000) {
                val expectedExists = composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_${fixture.purchaseMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
                val excludedExists = composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_${fixture.wasteMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
                expectedExists && !excludedExists
            }
            
            // Only matching row remains
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}").assertDoesNotExist()
            
            // Verify summary reflects filtered result
            composeTestRule.onNodeWithTag("inventory_activity_list").performScrollToNode(hasTestTag("inventory_activity_summary"))
            composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextEquals("1")

            // 5. Active search indicator visible
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertIsDisplayed()

            // 6. Reset search via stable remove tag
            composeTestRule.onNodeWithTag("inventory_activity_active_search_remove").performClick()
            
            // 7. Wait for default results to return
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_${fixture.wasteMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("")
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertDoesNotExist()
            
            composeTestRule.onNodeWithTag("inventory_activity_list").performScrollToNode(hasTestTag("inventory_activity_summary"))
            composeTestRule.onNodeWithTag("inventory_activity_movement_count").assertTextEquals("7")

            // 7.5. Re-search to reach filtered-empty state
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("NONEXISTENT_ACTIVITY")
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_filtered_empty")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty_reset").performClick()
            
            // Wait for return
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_row_${fixture.purchaseMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify reset from filtered-empty also works
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("")
            scrollToActivityRow(fixture.purchaseMovementId)
            
            // 8. Open Production consumption row -> Production Batch source
            scrollToActivityRow(fixture.productionConsumptionMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}")
                .performClick()
            
            composeTestRule.onNodeWithTag("inventory_activity_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.onNodeWithTag("production_batch_detail_screen").assertIsDisplayed()
            
            // Back from Source Document -> Activity Detail
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}").assertIsDisplayed()
            
            // Back from Activity Detail -> Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            
            // 9. Reversal -> Original -> Navigation
            scrollToActivityRow(fixture.reversalMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}")
                .performClick()
            
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.reversalMovementId.value}").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.originalMovementId.value}").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("inventory_activity_open_reversal").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_open_original").assertDoesNotExist()
            
            // Press Back once from related Detail -> Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_detail_screen").assertDoesNotExist()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    private fun scrollToActivityRow(movementId: com.miara.cuentame.core.common.ids.InventoryMovementId) {
        val rowTag = "inventory_activity_row_${movementId.value}"
        composeTestRule.onNodeWithTag("inventory_activity_list").performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed()
    }
}
