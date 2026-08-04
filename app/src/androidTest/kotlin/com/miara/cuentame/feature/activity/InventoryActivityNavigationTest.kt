package com.miara.cuentame.feature.activity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
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
    @Inject lateinit var restaurantRepository: RestaurantRepository
    @Inject lateinit var ingredientRepository: IngredientRepository
    @Inject lateinit var areaRepository: InventoryAreaRepository
    @Inject lateinit var purchaseRepository: PurchaseRepository
    @Inject lateinit var wasteRepository: WasteRepository
    @Inject lateinit var stockCountRepository: StockCountRepository
    @Inject lateinit var productionBatchRepository: ProductionBatchRepository
    @Inject lateinit var preparationRecipeRepository: PreparationRecipeRepository
    @Inject lateinit var activityRepository: InventoryActivityRepository

    private lateinit var fixture: CanonicalInventoryActivityFixture

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.seedBaseline()
            fixture = seedCanonicalInventoryActivity(
                restaurantRepository, ingredientRepository, areaRepository,
                purchaseRepository, wasteRepository, stockCountRepository,
                productionBatchRepository, preparationRecipeRepository, activityRepository
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
    fun activityNavigation_fromHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Activity from Home
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("inventory_activity_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inventory_activity_active_ingredient_filter").assertDoesNotExist()
            composeTestRule.onNodeWithTag("inventory_activity_active_area_filter").assertDoesNotExist()
            
            // Back returns home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun activityNavigation_fromIngredientDetail() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Ingredients
            composeTestRule.onNodeWithTag("nav_inventory").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_list")).fetchSemanticsNodes().isNotEmpty()
            }

            // 2. Open our seeded Ingredient
            composeTestRule.onNodeWithTag("ingredient_item_Chicken").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("ingredient_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 3. View Activity
            composeTestRule.onNodeWithTag("ingredient_view_activity").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify prefilter via active chip
            composeTestRule.onNodeWithTag("inventory_activity_active_ingredient_filter").assertTextContains("Chicken")
            // And matching rows exist
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").assertIsDisplayed()
        }
    }

    @Test
    fun activityNavigation_fromAreaDetail() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Open Settings
            composeTestRule.onNodeWithTag("nav_settings").performClick()

            // 2. Open Areas
            composeTestRule.onNodeWithTag("settings_areas").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasText("Storage")).fetchSemanticsNodes().isNotEmpty()
            }

            // 3. Open overflow menu for the seeded Area
            composeTestRule.onNodeWithTag("area_menu_${fixture.areaId.value}").performClick()

            // 4. View Activity
            composeTestRule.onNodeWithTag("area_view_activity_${fixture.areaId.value}").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify prefilter
            composeTestRule.onNodeWithTag("inventory_activity_active_area_filter").assertTextContains("Storage")
        }
    }

    @Test
    fun activityNavigation_listToDetailAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Open Activity
            composeTestRule.onNodeWithTag("open_inventory_activity_button").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Search
            composeTestRule.onNodeWithTag("inventory_activity_search").performTextInput("Chicken")

            // 2. Open Detail
            composeTestRule.onNodeWithTag("inventory_activity_row_${fixture.purchaseMovementId.value}").performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("inventory_activity_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }

            // 3. Go back
            composeTestRule.onNodeWithContentDescription("Back").performClick()

            // 4. Verify search and filter are preserved
            composeTestRule.onNodeWithTag("inventory_activity_search").assertTextEquals("Chicken")
        }
    }
}
