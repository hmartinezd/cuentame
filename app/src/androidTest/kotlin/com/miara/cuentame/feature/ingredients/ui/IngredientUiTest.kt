package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.miara.cuentame.MainActivity
import com.miara.cuentame.feature.waste.ui.waitForTag
import com.miara.cuentame.R
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat

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

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()

            database.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            // Seed a restaurant as well
            database.restaurantDao().insert(com.miara.cuentame.core.model.restaurant.Restaurant(
                com.miara.cuentame.core.common.ids.RestaurantId("rest_ing_test"), "Test Ing Rest", "USD", "en-US", java.time.Instant.now(), java.time.Instant.now(), null
            ).toEntity())
            database.inventoryAreaDao().upsert(com.miara.cuentame.core.model.inventory.InventoryArea(
                com.miara.cuentame.core.common.ids.InventoryAreaId("area_ing_test"), com.miara.cuentame.core.common.ids.RestaurantId("rest_ing_test"), "Area 1", "area 1", 0, true, java.time.Instant.now(), java.time.Instant.now(), null
            ).toEntity())
            
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
        }
    }

    @Test
    fun complete_ingredient_e2e_flow() {
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
                composeTestRule.onAllNodesWithText("Mass").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Mass").performClick()
            composeTestRule.waitForIdle()

            // Select Base Unit: Pound
            composeTestRule.onNodeWithTag("base_unit_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Pound").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Pound").performClick()
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
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("package_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("package_name_input").performTextInput("Case")
            composeTestRule.onNodeWithTag("package_factor_input").performTextInput("40")
            composeTestRule.onNodeWithTag("package_dialog_confirm").performClick()
            composeTestRule.waitForIdle()
            
            // Save Ingredient
            composeTestRule.onNodeWithTag("ingredient_form_save").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            // 3. Verify Detail
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()

            // 4. Reopen and verify persistence
            composeTestRule.onNodeWithTag("ingredient_detail_back").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText("Chicken Breast").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").performClick()
            composeTestRule.waitForIdle()
            
            // 5. Test Read-only Edit
            composeTestRule.onNodeWithContentDescription("Edit").performClick()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify units are read-only (no add standard unit button)
            composeTestRule.onAllNodesWithText("Add Standard Unit").assertCountEquals(0)
            
            // Verify delete buttons are absent
            composeTestRule.onAllNodes(hasContentDescription("Remove", substring = true)).assertCountEquals(0)

            // Save (no changes)
            composeTestRule.onNodeWithTag("ingredient_form_save").performClick()
            
            // Back to detail
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("ingredient_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Chicken Breast").assertIsDisplayed()
        }
    }

    private fun waitForHome() {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("app_loading").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
