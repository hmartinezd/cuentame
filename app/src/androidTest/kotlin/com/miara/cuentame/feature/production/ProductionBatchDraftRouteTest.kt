package com.miara.cuentame.feature.production

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductionBatchDraftRouteTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    private val restaurantId = "r1"
    private val batchId = "batch1"

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
            
            // Seed data
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en-US", 0, 0, null))
            database.inventoryAreaDao().upsert(InventoryAreaEntity("a1", restaurantId, "Area", "area", 0, true, 0, 0, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            database.ingredientDao().insert(IngredientEntity("i1", restaurantId, "Ing", "ing", null, "u1", "a1", null, null, null, true, 0, 0, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("o1", "i1", "Unit", "u", "u1", BigDecimal.ONE, true, true, true, true, 0, 0, null))

            database.productionBatchDao().insert(ProductionBatchEntity(
                id = batchId, restaurantId = restaurantId, recipeId = "rec1",
                recipeNameSnapshot = "Recipe", outputIngredientId = "i1",
                batchMultiplier = "1", recipeStandardYieldQuantitySnapshot = "10",
                recipeStandardYieldBaseSnapshot = "10", recipeYieldUnitOptionIdSnapshot = "o1",
                expectedOutputQuantityEntered = "10", expectedOutputQuantityBase = "10",
                actualOutputQuantityEntered = "10", actualOutputQuantityBase = "10",
                outputUnitOptionId = "o1", outputAreaId = "a1", hasManualOutputQuantityOverride = false,
                totalComponentCostSnapshot = null, outputUnitCostBaseSnapshot = null,
                effectiveAt = System.currentTimeMillis(), status = "DRAFT", notes = null,
                createdAt = 0, updatedAt = 0, postedAt = null, voidedAt = null
            ))

            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun reviewGuard_dirtyForm_blocksNavigation() {
        navigateToDraft()

        // Make form dirty
        composeTestRule.onNodeWithTag("production_multiplier_field").performTextReplacement("2")
        
        // Try Review
        composeTestRule.onNodeWithTag("production_batch_review").assertIsNotEnabled()
    }

    @Test
    fun reviewGuard_cleanForm_allowsNavigation() {
        navigateToDraft()

        // Form is clean initially
        composeTestRule.onNodeWithTag("production_batch_review").assertIsEnabled().performClick()
        
        // Check if we are on Preview screen
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("production_batch_preview_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun navigateToDraft() {
        composeTestRule.onNodeWithTag("open_production_batches_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Recipe").performClick() // Click the batch in the list
    }
}
