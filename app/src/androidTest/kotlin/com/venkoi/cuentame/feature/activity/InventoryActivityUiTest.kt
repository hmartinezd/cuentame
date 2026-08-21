package com.venkoi.cuentame.feature.activity

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.core.database.dao.InventoryMovementDao
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.test.TestStateManager
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
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_inventory_activity_button"))
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
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
            composeTestRule.onNodeWithTag("inventory_activity_movement_count_value").assertTextEquals("7")
            composeTestRule.onNodeWithTag("inventory_activity_incoming_count_value").assertTextEquals("4")
            composeTestRule.onNodeWithTag("inventory_activity_outgoing_count_value").assertTextEquals("3")
            composeTestRule.onNodeWithTag("inventory_activity_reversal_count_value").assertTextEquals("1")

            // 4. Search by unique Invoice Number
            val searchQuery = "INV-001"
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput(searchQuery)
            
            // Wait for debounced search using Summary count
            waitForMovementCount("1")
            
            // Only matching row remains
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}").assertDoesNotExist()
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.originalMovementId.value}").assertDoesNotExist()

            // 5. Active search indicator visible
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertIsDisplayed()

            // 6. Reset search via stable remove tag
            composeTestRule.onNodeWithTag("inventory_activity_active_search_remove").performClick()
            
            // 7. Wait for default results to return using Summary count
            waitForMovementCount("7")
            
            composeTestRule.onNodeWithTag("inventory_activity_search").assert(SemanticsMatcher("is empty") { 
                it.config.getOrNull(SemanticsProperties.EditableText)?.text?.isEmpty() ?: true
            })
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertDoesNotExist()
            
            scrollToActivityRow(fixture.purchaseMovementId)
            scrollToActivityRow(fixture.wasteMovementId)

            // 7.5. Re-search to reach filtered-empty state
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("NONEXISTENT_ACTIVITY")
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_filtered_empty")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty_reset").performClick()
            
            // Wait for return using Summary count
            waitForMovementCount("7")
            
            // Verify reset from filtered-empty also works
            composeTestRule.onNodeWithTag("inventory_activity_search").assert(SemanticsMatcher("is empty") { 
                it.config.getOrNull(SemanticsProperties.EditableText)?.text?.isEmpty() ?: true
            })
            composeTestRule.onNodeWithTag("inventory_activity_filtered_empty").assertDoesNotExist()
            composeTestRule.onNodeWithTag("inventory_activity_active_search").assertDoesNotExist()
            scrollToActivityRow(fixture.purchaseMovementId)
            
            // 8. Open Production consumption row -> Production Batch source
            scrollToActivityRow(fixture.productionConsumptionMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}")
                .performClick()
            
            waitForActivityDetail(fixture.productionConsumptionMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForProductionBatchDetail()
            
            // Back from Source Document -> Activity Detail
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            waitForActivityDetail(fixture.productionConsumptionMovementId)
            
            // Back from Activity Detail -> Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            
            // 9. Reversal -> Original -> Navigation
            scrollToActivityRow(fixture.reversalMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}")
                .performClick()
            
            waitForActivityDetail(fixture.reversalMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            waitForActivityDetail(fixture.originalMovementId)
            
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
            composeTestRule.onAllNodes(hasTestTag("home_dashboard_list")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_dashboard_list").assertIsDisplayed()
    }

    private fun scrollToActivityRow(movementId: com.venkoi.cuentame.core.common.ids.InventoryMovementId) {
        val rowTag = "inventory_activity_row_${movementId.value}"
        composeTestRule.onNodeWithTag("inventory_activity_list").performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed()
    }

    private fun waitForMovementCount(expectedCount: String, timeoutMillis: Long = 5000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodes(hasTestTag("inventory_activity_movement_count_value") and hasText(expectedCount))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_activity_movement_count_value").assertTextEquals(expectedCount)
    }

    private fun waitForActivityDetail(movementId: com.venkoi.cuentame.core.common.ids.InventoryMovementId) {
        val tag = "inventory_activity_detail_movement_${movementId.value}"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_activity_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForProductionBatchDetail() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("production_batch_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("production_batch_detail_screen").assertIsDisplayed()
    }
}
