package com.venkoi.cuentame.feature.activity

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
class InventoryActivityNavigationTest {

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
    fun homeToActivityAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_inventory_activity_button"))
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_back").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun prefilteredActivity_fromIngredientDetail() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            composeTestRule.onNodeWithTag("nav_inventory").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list")).fetchSemanticsNodes().isNotEmpty()
            }

            val chickenTag = "ingredient_item_${fixture.componentIngredientId.value}"
            composeTestRule.onNodeWithTag("ingredient_list").performScrollToNode(hasTestTag(chickenTag))
            composeTestRule.onNodeWithTag(chickenTag)
                .performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("ingredient_view_activity").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onAllNodesWithText("Chicken", substring = true).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun prefilteredActivity_fromAreaOverflow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.onNode(hasText("Inventory Areas") and hasClickAction()).performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasText("Storage")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("area_menu_${fixture.areaId.value}").performClick()
            composeTestRule.onNodeWithTag("area_view_activity_${fixture.areaId.value}").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onAllNodesWithText("Storage", substring = true).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun activityListToDetailAndBack_preservesState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_inventory_activity_button"))
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("Chicken")

            scrollToActivityRow(fixture.purchaseMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.purchaseMovementId)

            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("Chicken")
        }
    }

    @Test
    fun detailToRelatedMovementStackInvariant() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_inventory_activity_button"))
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Reversal -> Original -> Reversal
            scrollToActivityRow(fixture.reversalMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}")
                .performClick()
            
            waitForActivityDetail(fixture.reversalMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            waitForActivityDetail(fixture.originalMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_reversal").performClick()
            waitForActivityDetail(fixture.reversalMovementId)
            
            // Press Back once -> Returns to Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            // Generic Activity Detail tag does not exist
            composeTestRule.onNodeWithTag("inventory_activity_detail_screen").assertDoesNotExist()
        }
    }

    @Test
    fun sourceDocumentNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_inventory_activity_button"))
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_list")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Purchase
            scrollToActivityRow(fixture.purchaseMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.purchaseMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForPurchaseDetail()
            composeTestRule.onNodeWithTag("purchase_detail_back").performClick()
            waitForActivityDetail(fixture.purchaseMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()

            // Waste
            scrollToActivityRow(fixture.wasteMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.wasteMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForWasteDetail()
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            waitForActivityDetail(fixture.wasteMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()

            // Stock Count
            scrollToActivityRow(fixture.stockCountMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.stockCountMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.stockCountMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForStockCountDetail()
            composeTestRule.onNodeWithTag("stock_count_detail_back").performClick()
            waitForActivityDetail(fixture.stockCountMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()

            // Production Batch - Consumption
            scrollToActivityRow(fixture.productionConsumptionMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.productionConsumptionMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForProductionBatchDetail()
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            waitForActivityDetail(fixture.productionConsumptionMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()

            // Production Batch - Output
            scrollToActivityRow(fixture.productionOutputMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionOutputMovementId.value}")
                .performClick()
            waitForActivityDetail(fixture.productionOutputMovementId)
            
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            waitForProductionBatchDetail()
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            waitForActivityDetail(fixture.productionOutputMovementId)
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("inventory_activity_list", useUnmergedTree = true).performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed()
    }

    private fun waitForActivityDetail(movementId: com.venkoi.cuentame.core.common.ids.InventoryMovementId) {
        val tag = "inventory_activity_detail_movement_${movementId.value}"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_activity_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForPurchaseDetail() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("purchase_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
    }

    private fun waitForWasteDetail() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("waste_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("waste_detail_screen").assertIsDisplayed()
    }

    private fun waitForStockCountDetail() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("count_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("count_detail_screen").assertIsDisplayed()
    }

    private fun waitForProductionBatchDetail() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("production_batch_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("production_batch_detail_screen").assertIsDisplayed()
    }
}
