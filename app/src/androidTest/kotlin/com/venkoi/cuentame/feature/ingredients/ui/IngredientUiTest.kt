package com.venkoi.cuentame.feature.ingredients.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.feature.waste.ui.waitForTag
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.mapper.toEntity
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.IngredientId

@HiltAndroidTest
class IngredientUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var testStateManager: com.venkoi.cuentame.test.TestStateManager

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()

            database.unitDao().insertSeedUnits(com.venkoi.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            // Seed a restaurant as well
            database.restaurantDao().insert(com.venkoi.cuentame.core.model.restaurant.Restaurant(
                com.venkoi.cuentame.core.common.ids.RestaurantId("rest_ing_test"), "Test Ing Rest", "USD", "en-US", java.time.Instant.now(), java.time.Instant.now(), null
            ).toEntity())
            database.inventoryAreaDao().upsert(com.venkoi.cuentame.core.model.inventory.InventoryArea(
                com.venkoi.cuentame.core.common.ids.InventoryAreaId("area_ing_test"), com.venkoi.cuentame.core.common.ids.RestaurantId("rest_ing_test"), "Area 1", "area 1", 0, true, java.time.Instant.now(), java.time.Instant.now(), null
            ).toEntity())
            
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun complete_ingredient_e2e_flow() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Navigate to Inventory
            composeTestRule.onNodeWithTag("nav_inventory", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()

            // 2. Create Chicken Breast
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("add_ingredient_fab").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_ingredient_fab").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_name_input").performTextInput("Chicken Breast")
            
            // Select Dimension: Mass
            composeTestRule.onNodeWithTag("dimension_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("dimension_item_MASS").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("dimension_item_MASS").performClick()
            composeTestRule.waitForIdle()

            // Select Base Unit: Pound
            composeTestRule.onNodeWithTag("base_unit_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("base_unit_item_mass_lb").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("base_unit_item_mass_lb").performClick()
            composeTestRule.waitForIdle()

            // Add Ounce Standard Unit
            composeTestRule.onNodeWithTag("add_standard_unit_button").performScrollTo().performClick()
            composeTestRule.waitForTag("unit_item_Ounce")
            composeTestRule.onNodeWithTag("unit_item_Ounce").performClick()
            
            // Check preview
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("1 oz = 0.0625 lb", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("standard_unit_dialog_confirm").performClick()
            composeTestRule.waitForIdle()

            // Add Case Package
            composeTestRule.onNodeWithTag("add_package_option_button").performScrollTo().performClick()
            composeTestRule.waitForTag("package_dialog")
            composeTestRule.onNodeWithTag("package_name_input").performTextInput("Case")
            composeTestRule.onNodeWithTag("package_factor_input").performTextInput("40")
            
            composeTestRule.onNodeWithTag("package_name_input").assertTextContains("Case")
            composeTestRule.onNodeWithTag("package_factor_input").assertTextContains("40")
            
            composeTestRule.onNodeWithTag("package_dialog_confirm").assertIsEnabled().performClick()
            composeTestRule.waitForIdle()
            
            // Save Ingredient
            composeTestRule.onNodeWithTag("ingredient_form_save").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // Verify units persisted
            val ingredients = runBlocking { database.ingredientDao().getActiveIngredients("rest_ing_test") }
            val ingIdString = ingredients.first().id
            val options = runBlocking { database.ingredientUnitOptionDao().getActiveOptions(ingIdString) }

            // List-origin creation returns to the list; open the created item to verify it.
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_$ingIdString").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_$ingIdString").performClick()
            composeTestRule.waitForTag("ingredient_detail_screen")
            composeTestRule.onNodeWithTag("ingredient_status").assertTextContains(context.getString(R.string.active))
            
            // Base unit is Pound, symbol lb -> displayName is lb
            val lbOpt = options.find { it.displayName == "lb" }
                ?: throw AssertionError("lb option not found. Options: ${options.map { it.displayName }}")
            // oz standard unit added -> displayName is oz (symbol)
            val ozOpt = options.find { it.displayName == "oz" }
                ?: throw AssertionError("oz option not found. Options: ${options.map { it.displayName }}")
            // Case package unit added -> displayName is Case
            val caseOpt = options.find { it.displayName == "Case" }
                ?: throw AssertionError("Case option not found. Options: ${options.map { it.displayName }}")
            
            composeTestRule.onNodeWithTag("ingredient_option_name_${lbOpt.id}", useUnmergedTree = true).assertTextContains("lb")
            composeTestRule.onNodeWithTag("ingredient_option_name_${ozOpt.id}", useUnmergedTree = true).assertTextContains("oz")
            composeTestRule.onNodeWithTag("ingredient_option_name_${caseOpt.id}", useUnmergedTree = true).assertTextContains("Case")
            composeTestRule.onNodeWithTag("ingredient_option_factor_${caseOpt.id}", useUnmergedTree = true).assertTextContains("40", substring = true)

            // 4. Reopen and verify persistence
            composeTestRule.onNodeWithTag("ingredient_detail_back").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_item_$ingIdString").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("ingredient_item_$ingIdString").performClick()
            composeTestRule.waitForIdle()
            
            // 5. Test Read-only Edit
            composeTestRule.waitForTag("ingredient_detail_screen")
            composeTestRule.onNodeWithTag("ingredient_edit_button").performClick()
            composeTestRule.waitForTag("ingredient_form_screen")
            
            // Verify units are read-only (no add standard unit button)
            composeTestRule.onNodeWithTag("add_standard_unit_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("add_package_option_button").assertDoesNotExist()
            
            // Verify base unit selector is disabled or name is displayed read-only
            composeTestRule.onNodeWithTag("base_unit_selector").assertDoesNotExist()

            // Save (no changes)
            composeTestRule.onNodeWithTag("ingredient_form_save").performScrollTo().performClick()
            
            // Back to detail
            composeTestRule.waitForTag("ingredient_detail_screen")
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
        }
    }

    @Test
    fun inventory_search_keeps_typed_text_visible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("nav_inventory", useUnmergedTree = true).performClick()
            composeTestRule.waitUntil(60_000) {
                composeTestRule.onAllNodesWithTag("ingredient_search_field").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("ingredient_search_field").performTextInput("Chicken")
            composeTestRule.onNodeWithTag("ingredient_search_field").assertTextContains("Chicken")
        }
    }

    private fun waitForHome() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("dashboard_restaurant_name").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
