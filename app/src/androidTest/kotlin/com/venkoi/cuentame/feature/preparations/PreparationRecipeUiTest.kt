package com.venkoi.cuentame.feature.preparations

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.backup.api.RestoreStartupState
import com.venkoi.cuentame.core.backup.internal.RestoreOperationGate
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import com.venkoi.cuentame.test.TestSeeder
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PreparationRecipeUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val timeoutRule: Timeout = Timeout.seconds(60)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    private val context get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

    private val restaurantId = RestaurantId(TestSeeder.RESTAURANT_ID)

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            
            // Seed additional ingredients needed for these tests
            seedIngredient("i2", "Water")
            seedIngredient("out1", "Onion Soup")
            // "i1" (Onion) is already seeded as ING_ID in baseline, but let's make it explicit if needed.
            // Actually baseline seeds "ing-test-1" as "Chicken".
            // I'll seed "i1" as "Onion"
            seedIngredient("i1", "Onion")
        }
    }

    private suspend fun seedIngredient(id: String, name: String) {
        database.ingredientDao().insert(IngredientEntity(
            id = id, restaurantId = restaurantId.value, name = name, normalizedName = name.lowercase(),
            categoryId = null, baseUnitId = TestSeeder.UNIT_ID, defaultAreaId = TestSeeder.AREA_ID, sku = null, notes = null,
            reorderPointBase = null, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
        ))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
            id = "o-$id", ingredientId = id, displayName = "Unit", shortLabel = "u",
            standardUnitId = TestSeeder.UNIT_ID, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
            isDefaultPurchase = true, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
        ))
    }

    @After
    fun teardown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun create_recipe_e2e_flow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Navigate to Preparation Recipes from Home
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            waitForTag("preparation_recipe_list_screen")
            composeTestRule.onNodeWithTag("preparation_list_back").assertIsDisplayed()

            // 2. Click Add FAB
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").assertIsDisplayed().performClick()
            waitForTag("preparation_recipe_editor_screen")

            // 3. Select Output Ingredient
            composeTestRule.onNodeWithTag("preparation_back_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("recipe_editor_save").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("recipe_output_ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_out1").performClick()
            
            // 4. Fill Header
            composeTestRule.onNodeWithTag("recipe_yield_quantity_field").performTextInput("10")
            composeTestRule.onNodeWithTag("recipe_yield_unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-out1").performClick()
            
            // 5. Save Draft
            composeTestRule.onNodeWithTag("recipe_editor_save").assertIsDisplayed().performClick()
            waitForTag("add_recipe_component")

            // 6. Add Component
            composeTestRule.onNodeWithTag("add_recipe_component", useUnmergedTree = true).assertIsDisplayed().performClick()
            waitForTag("preparation_recipe_component_screen")

            composeTestRule.onNodeWithTag("recipe_component_ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_i1").performClick()
            composeTestRule.onNodeWithTag("recipe_component_quantity_field").performTextInput("5")
            composeTestRule.onNodeWithTag("recipe_component_unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-i1").performClick()
            composeTestRule.onNodeWithTag("recipe_component_save").assertIsDisplayed().performClick()
            
            waitForTag("preparation_recipe_editor_screen")

            // 7. Verify Component in List
            val recipe = runBlocking { database.preparationRecipeDao().getActiveOrDraftByOutputIngredient(restaurantId.value, "out1") }
            val components = runBlocking { database.preparationRecipeDao().getComponentsForRecipe(recipe!!.id) }
            val componentId = components.first { it.componentIngredientId == "i1" }.id

            composeTestRule.onNodeWithTag("recipe_component_item_$componentId", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("Onion").assertExists()
            composeTestRule.onNodeWithText("5 Unit").assertExists()

            // 8. Verify Database Graph Exact Assertions
            assertEquals(com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus.DRAFT.name, recipe!!.status)
            assertEquals("Onion Soup", recipe.name)
            assertEquals(0, BigDecimal("10").compareTo(recipe.standardYieldQuantity))
            assertEquals("o-out1", recipe.yieldUnitOptionId)
            assertEquals(1, components.size)
            assertEquals("i1", components[0].componentIngredientId)
            assertEquals("o-i1", components[0].unitOptionId)
            assertEquals(0, BigDecimal("5").compareTo(components[0].quantityEntered))
            assertEquals(0, components[0].sortOrder)
        }
    }

    @Test
    fun recipe_editor_save_validation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            waitForTag("preparation_recipe_list_screen")
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").performClick()
            waitForTag("preparation_recipe_editor_screen")
            
            // Save without output ingredient
            composeTestRule.onNodeWithTag("recipe_editor_save").performClick()
            composeTestRule.onNodeWithText(context.getString(R.string.error_select_output_ingredient)).assertIsDisplayed()
        }
    }

    @Test
    fun recipe_editor_discard_confirmation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            waitForTag("preparation_recipe_list_screen")
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").performClick()
            waitForTag("preparation_recipe_editor_screen")
            
            // Modify name
            composeTestRule.onNodeWithTag("recipe_name_field").performTextInput("Dirty")
            
            // Try back
            composeTestRule.onNodeWithTag("preparation_back_button").performClick()
            
            // Confirm dialog
            composeTestRule.onNodeWithText(context.getString(R.string.discard_changes_title)).assertIsDisplayed()
            
            // Stay
            composeTestRule.onNodeWithText(context.getString(R.string.action_stay)).performClick()
            composeTestRule.onNodeWithTag("preparation_recipe_editor_screen").assertIsDisplayed()
            
            // Try back again and discard
            composeTestRule.onNodeWithTag("preparation_back_button").performClick()
            composeTestRule.onNodeWithText(context.getString(R.string.action_discard)).performClick()
            
            waitForTag("preparation_recipe_list_screen")
        }
    }

    @Test
    fun component_editor_cancel_returns_to_draft() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            waitForTag("preparation_recipe_list_screen")
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").performClick()
            waitForTag("preparation_recipe_editor_screen")
            
            // First save header to get into Draft mode
            composeTestRule.onNodeWithTag("recipe_output_ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_out1").performClick()
            composeTestRule.onNodeWithTag("recipe_editor_save").performClick()
            waitForTag("add_recipe_component")
            
            // Add component
            composeTestRule.onNodeWithTag("add_recipe_component", useUnmergedTree = true).performClick()
            waitForTag("preparation_recipe_component_screen")
            
            // Cancel
            composeTestRule.onNodeWithTag("preparation_back_button").performClick()
            waitForTag("preparation_recipe_editor_screen")
            
            // Verify no component was added
            composeTestRule.onNodeWithText("Onion").assertDoesNotExist()
        }
    }

    @Test
    fun recipe_detail_back_returns_to_list() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()
            
            // Seed an active recipe manually in DB
            runBlocking {
                database.preparationRecipeDao().insert(PreparationRecipeEntity(
                    id = "r-active", 
                    restaurantId = restaurantId.value, 
                    outputIngredientId = "out1",
                    name = "Active Recipe", 
                    normalizedName = "active recipe",
                    status = com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus.ACTIVE.name,
                    standardYieldQuantity = BigDecimal.TEN, 
                    standardYieldQuantityBase = BigDecimal.TEN,
                    yieldUnitOptionId = "o-out1",
                    notes = null, 
                    createdAt = 0, 
                    updatedAt = 0, 
                    archivedAt = null
                ))
            }
            
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_preparation_recipes_button"))
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performClick()
            waitForTag("preparation_recipe_list_screen")
            
            // Click the recipe to go to details
            composeTestRule.onNodeWithTag("preparation_recipe_item_r-active").performClick()
            waitForTag("preparation_recipe_detail_screen")
            
            // Back
            composeTestRule.onNodeWithTag("preparation_back_button").performClick()
            waitForTag("preparation_recipe_list_screen")
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(
            conditionDescription = "Home dashboard list did not appear",
            timeoutMillis = 15_000
        ) {
            composeTestRule
                .onAllNodesWithTag("home_dashboard_list")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeTestRule.waitUntil(
            conditionDescription = "Node with tag $tag did not appear",
            timeoutMillis = timeoutMillis
        ) {
            composeTestRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
