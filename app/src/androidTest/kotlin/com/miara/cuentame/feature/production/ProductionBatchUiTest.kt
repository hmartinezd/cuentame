package com.miara.cuentame.feature.production

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductionBatchUiTest {

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
            database.inventoryAreaDao().upsert(InventoryAreaEntity("a1", restaurantId.value, "Kitchen", "kitchen", 0, true, 0, 0, null))
            database.inventoryAreaDao().upsert(InventoryAreaEntity("a2", restaurantId.value, "Bar", "bar", 1, true, 0, 0, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            seedIngredient("i1", "Raw Beef")
            seedIngredient("out1", "Ground Beef")
            
            // Seed Active Recipe
            database.preparationRecipeDao().insert(PreparationRecipeEntity(
                id = "rec1", restaurantId = restaurantId.value, outputIngredientId = "out1",
                name = "Grounding", normalizedName = "grounding",
                standardYieldQuantity = BigDecimal("10"), standardYieldQuantityBase = BigDecimal("10"),
                yieldUnitOptionId = "o-out1", status = "ACTIVE", notes = null, createdAt = 0, updatedAt = 0, archivedAt = null
            ))
            database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity(
                id = "comp1", recipeId = "rec1", componentIngredientId = "i1",
                quantityEntered = BigDecimal("12"), quantityBase = BigDecimal("12"),
                unitOptionId = "o-i1", sortOrder = 0, notes = null, createdAt = 0, updatedAt = 0
            ))

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
        runBlocking { database.clearAllTables() }
    }

    @Test
    fun complete_production_lifecycle_e2e() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Open Production List from Home
            composeTestRule.onNodeWithTag("open_production_batches_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            // 2. Create Draft
            composeTestRule.onNodeWithTag("add_production_batch_fab").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("production_recipe_selector").performClick()
            composeTestRule.onNodeWithText("Grounding").performClick()
            
            composeTestRule.onNodeWithTag("production_multiplier_field").performTextReplacement("2")
            composeTestRule.onNodeWithTag("production_output_area_selector").performClick()
            composeTestRule.onNodeWithText("Kitchen").performClick()
            
            composeTestRule.onNodeWithTag("production_batch_create").performClick()
            composeTestRule.waitForIdle()

            // 3. Verify Draft state
            val batchSummaries = runBlocking { database.productionBatchDao().observeSummaries(restaurantId.value, null).first() }
            val summary = batchSummaries.firstOrNull()
            assertNotNull(summary)
            assertEquals("DRAFT", summary!!.status)
            
            val batch = runBlocking { database.productionBatchDao().getById(summary.id) }
            assertNotNull(batch)
            assertEquals(0, BigDecimal("2").compareTo(BigDecimal(batch!!.batchMultiplier)))
            
            // 4. Open Component and Set Area
            val batchComponents = runBlocking { database.productionBatchDao().getComponents(batch.id) }
            val compId = batchComponents.first().id
            composeTestRule.onNodeWithTag("production_component_item_$compId").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("production_component_area_selector").performClick()
            composeTestRule.onNodeWithText("Kitchen").performClick()
            composeTestRule.onNodeWithTag("production_batch_save").performClick()
            composeTestRule.waitForIdle()

            // 5. Review and Post
            composeTestRule.onNodeWithTag("production_batch_review").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.onNodeWithTag("production_batch_post").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            composeTestRule.waitForIdle()

            // 6. Verify Posted Detail
            composeTestRule.onNodeWithTag("production_batch_detail_screen").assertExists()
            composeTestRule.onNodeWithText("Posted").assertExists()
            
            // 7. Void
            composeTestRule.onNodeWithTag("production_batch_void").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            composeTestRule.waitForIdle()
            
            // 8. Verify Voided state
            composeTestRule.onNodeWithText("Voided").assertExists()
            val finalBatch = runBlocking { database.productionBatchDao().getById(batch.id) }
            assertEquals("VOIDED", finalBatch!!.status)
            assertNotNull(finalBatch.voidedAt)
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
