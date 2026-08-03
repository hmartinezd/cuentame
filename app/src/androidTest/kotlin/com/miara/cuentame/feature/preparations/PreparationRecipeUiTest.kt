package com.miara.cuentame.feature.preparations

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    private val restaurantId = RestaurantId("r1")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
            
            // 1. Restaurant
            database.restaurantDao().insert(RestaurantEntity(restaurantId.value, "Test Rest", "USD", "en-US", 0, 0, null))
            
            // 2. Inventory Area
            database.inventoryAreaDao().upsert(InventoryAreaEntity("a1", restaurantId.value, "Stock", "stock", 0, true, 0, 0, null))

            // 3. Unit
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            // 4. Ingredients & unit options
            seedIngredient("i1", "Onion")
            seedIngredient("i2", "Water")
            seedIngredient("out1", "Onion Soup")

            // 5. Preferences
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    private suspend fun seedIngredient(id: String, name: String) {
        database.ingredientDao().insert(IngredientEntity(
            id = id, restaurantId = restaurantId.value, name = name, normalizedName = name.lowercase(),
            categoryId = null, baseUnitId = "u1", defaultAreaId = "a1", sku = null, notes = null,
            reorderPointBase = null, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
        ))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
            id = "o-$id", ingredientId = id, displayName = "Unit", shortLabel = "u",
            standardUnitId = "u1", factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
            isDefaultPurchase = true, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
        ))
    }

    @After
    fun teardown() {
        runBlocking {
            database.clearAllTables()
        }
    }

    @Test
    fun create_recipe_e2e_flow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Navigate to Preparation Recipes from Home
            composeTestRule.onNodeWithTag("open_preparation_recipes_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            // 2. Click Add FAB
            composeTestRule.onNodeWithTag("add_preparation_recipe_fab").performClick()
            composeTestRule.waitForIdle()

            // 3. Select Output Ingredient
            composeTestRule.onNodeWithTag("recipe_output_ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_out1").performClick()
            
            // 4. Fill Header
            // Recipe name can be blank - should default to ingredient name
            composeTestRule.onNodeWithTag("recipe_yield_quantity_field").performTextInput("10")
            composeTestRule.onNodeWithTag("recipe_yield_unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-out1").performClick()
            
            // 5. Save Draft
            composeTestRule.onNodeWithTag("recipe_editor_save").performClick()
            composeTestRule.waitForIdle()

            // 6. Add Component
            composeTestRule.onNodeWithTag("add_recipe_component").performClick()
            composeTestRule.onNodeWithTag("recipe_component_ingredient_selector").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_i1").performClick()
            composeTestRule.onNodeWithTag("recipe_component_quantity_field").performTextInput("5")
            composeTestRule.onNodeWithTag("recipe_component_unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-i1").performClick()
            composeTestRule.onNodeWithTag("recipe_component_save").performClick()
            composeTestRule.waitForIdle()

            // 7. Verify Component in List
            // Find component ID from DB
            val recipe = runBlocking { database.preparationRecipeDao().getActiveOrDraftByOutputIngredient(restaurantId.value, "out1") }
            val components = runBlocking { database.preparationRecipeDao().getComponentsForRecipe(recipe!!.id) }
            val componentId = components.first { it.componentIngredientId == "i1" }.id

            composeTestRule.onNodeWithTag("recipe_component_item_$componentId").assertExists()
            composeTestRule.onNodeWithText("Onion").assertExists()
            composeTestRule.onNodeWithText("5 Unit").assertExists()

            // 8. Verify Database Graph Exact Assertions
            assertEquals(com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus.DRAFT.name, recipe!!.status)
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
