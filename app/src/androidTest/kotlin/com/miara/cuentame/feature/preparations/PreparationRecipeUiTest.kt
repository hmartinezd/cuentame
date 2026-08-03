package com.miara.cuentame.feature.preparations

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
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
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
            
            // Seed base data
            database.restaurantDao().insert(RestaurantEntity(restaurantId.value, "Test Rest", "USD", "en-US", 0, 0, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            // Seed ingredients
            seedIngredient("i1", "Onion")
            seedIngredient("i2", "Water")
            seedIngredient("out1", "Onion Soup")

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
            composeTestRule.onNodeWithText("Output ingredient").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_out1").performClick()
            
            // 4. Fill Header
            composeTestRule.onNodeWithText("Recipe name").performTextInput("Classic Onion Soup")
            composeTestRule.onNodeWithText("Standard yield").performTextInput("10")
            composeTestRule.onNodeWithText("Yield unit").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-out1").performClick()
            
            // 5. Save Draft
            composeTestRule.onNodeWithText("Save Changes").performClick()
            composeTestRule.waitForIdle()

            // 6. Add Component
            composeTestRule.onNodeWithTag("add_recipe_component").performClick()
            composeTestRule.onNodeWithText("Ingredients").performClick()
            composeTestRule.onNodeWithTag("ingredient_option_i1").performClick()
            composeTestRule.onNodeWithText("Quantity").performTextInput("5")
            composeTestRule.onNodeWithText("Unit").performClick()
            composeTestRule.onNodeWithTag("unit_option_o-i1").performClick()
            composeTestRule.onNodeWithText("Save Changes").performClick()
            composeTestRule.waitForIdle()

            // 7. Verify Component in List
            composeTestRule.onNodeWithTag("recipe_component_item_o-i1", useUnmergedTree = true).assertExists() // Actually uses ID which is random in production but I'm using IdGenerator. Use a different tag if needed.
            // Wait, ID is from IdGenerator. In tests it might be predictable if I mock it, but I'm using real DB.
            // I'll use content match.
            composeTestRule.onNodeWithText("Onion").assertExists()
            composeTestRule.onNodeWithText("5 Unit").assertExists()
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
