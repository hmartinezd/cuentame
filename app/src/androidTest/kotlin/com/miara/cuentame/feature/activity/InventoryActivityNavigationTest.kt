package com.miara.cuentame.feature.activity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.domain.repository.*
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
    fun homeToActivityAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

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
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("nav_inventory").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("ingredient_item_Chicken")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("ingredient_view_activity").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_active_ingredient_filter").assertTextContains("Chicken")
        }
    }

    @Test
    fun prefilteredActivity_fromAreaOverflow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.onNodeWithTag("settings_areas").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasText("Storage")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("area_menu_${fixture.areaId.value}").performClick()
            composeTestRule.onNodeWithTag("area_view_activity_${fixture.areaId.value}").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_active_area_filter").assertTextContains("Storage")
        }
    }

    @Test
    fun activityListToDetailAndBack_preservesState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("Chicken")

            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_movement_${fixture.purchaseMovementId.value}")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("Chicken")
        }
    }

    @Test
    fun detailToRelatedMovementStackInvariant() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Reversal -> Original -> Reversal
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.reversalMovementId.value}")
                .performScrollTo()
                .performClick()
            
            composeTestRule.onNodeWithTag("inventory_activity_open_original").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.originalMovementId.value}").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("inventory_activity_open_reversal").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.reversalMovementId.value}").assertIsDisplayed()
            
            // Press Back once -> Returns to Activity List
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
        }
    }

    @Test
    fun sourceDocumentNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Purchase
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("purchase_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.purchaseMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()

            // Waste
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.wasteMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.onNodeWithTag("waste_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("waste_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.wasteMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()

            // Stock Count
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.stockCountMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.onNodeWithTag("count_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("stock_count_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.stockCountMovementId.value}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_detail_back_top").performClick()

            // Production Batch
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.productionConsumptionMovementId.value}")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("inventory_activity_open_source").performClick()
            composeTestRule.onNodeWithTag("production_batch_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("production_batch_detail_back").performClick()
            composeTestRule.onNodeWithTag("inventory_activity_detail_movement_${fixture.productionConsumptionMovementId.value}").assertIsDisplayed()
        }
    }
}
