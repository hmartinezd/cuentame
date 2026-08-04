package com.miara.cuentame.feature.production

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductionBatchComposeTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
            
            // Seed minimum data to reach screens
            val restaurantId = "r1"
            val areaId = "a1"
            val ingredientId = "i1"
            val outputIngredientId = "out1"
            val unitId = "u1"
            val optionId = "o-i1"
            val outputOptionId = "o-out1"

            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en-US", 0, 0, null))
            database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Kitchen", "kitchen", 0, true, 0, 0, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            database.ingredientDao().insert(IngredientEntity(
                id = ingredientId, restaurantId = restaurantId, name = "Raw", normalizedName = "raw",
                categoryId = null, baseUnitId = unitId, defaultAreaId = areaId, sku = null, notes = null,
                reorderPointBase = null, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
            ))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
                id = optionId, ingredientId = ingredientId, displayName = "Unit", shortLabel = "u",
                standardUnitId = unitId, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
                isDefaultPurchase = true, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
            ))

            database.ingredientDao().insert(IngredientEntity(
                id = outputIngredientId, restaurantId = restaurantId, name = "Output", normalizedName = "output",
                categoryId = null, baseUnitId = unitId, defaultAreaId = areaId, sku = null, notes = null,
                reorderPointBase = null, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
            ))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
                id = outputOptionId, ingredientId = outputIngredientId, displayName = "Unit", shortLabel = "u",
                standardUnitId = unitId, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
                isDefaultPurchase = true, isActive = true, createdAt = 0, updatedAt = 0, deletedAt = null
            ))

            database.preparationRecipeDao().insert(PreparationRecipeEntity(
                id = "rec1", restaurantId = restaurantId, outputIngredientId = outputIngredientId,
                name = "Recipe", normalizedName = "recipe",
                standardYieldQuantity = BigDecimal("10"), standardYieldQuantityBase = BigDecimal("10"),
                yieldUnitOptionId = outputOptionId, status = "ACTIVE", notes = null, createdAt = 0, updatedAt = 0, archivedAt = null
            ))
            database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity(
                id = "comp1", recipeId = "rec1", componentIngredientId = ingredientId,
                quantityEntered = BigDecimal("1"), quantityBase = BigDecimal("1"),
                unitOptionId = optionId, sortOrder = 0, notes = null, createdAt = 0, updatedAt = 0
            ))

            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun dateSelection_updatesDisplayedDateLocally() {
        // Navigate to Production Create
        composeTestRule.onNodeWithTag("open_production_batches_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("add_production_batch_fab").performClick()
        
        // Open Date Picker
        composeTestRule.onNodeWithContentDescription("Choose effective date").performClick()
        
        // Select a specific date (e.g. 15th of current month/year if visible, or just pick one)
        // Material 3 DatePicker usually has text "15" for the day.
        // We'll try to find a day and click it. 
        // For determinism in tests, choosing "15" is usually safe if we don't care about the month.
        composeTestRule.onNodeWithText("15").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        
        // Verify displayed date contains "15"
        // The exact format depends on the locale, but for "en" it's usually "MMM 15, YYYY"
        val expectedDay = "15"
        composeTestRule.onNodeWithTag("production_effective_time").assert(hasAnyDescendant(hasText(expectedDay, substring = true)))
    }
}
