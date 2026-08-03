package com.miara.cuentame.feature.production

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestSeeder
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

    @Inject
    lateinit var purchaseRepository: PurchaseRepository

    private val restaurantId = RestaurantId("r1")
    private val areaId = InventoryAreaId("a1")
    private val ingredientId = IngredientId("i1")
    private val outputIngredientId = IngredientId("out1")
    private val unitId = "u1"
    private val optionId = IngredientUnitOptionId("o-i1")
    private val outputOptionId = IngredientUnitOptionId("o-out1")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
            
            // Seed base data
            database.restaurantDao().insert(RestaurantEntity(restaurantId.value, "Test Rest", "USD", "en-US", 0, 0, null))
            database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId.value, restaurantId.value, "Kitchen", "kitchen", 0, true, 0, 0, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Unit", "u", "COUNT", BigDecimal.ONE, true, 0)))
            
            seedIngredient(ingredientId.value, "Raw Beef")
            seedIngredient(outputIngredientId.value, "Ground Beef")
            
            // Seed valid cost-bearing inventory: 100 base units @ 5 USD
            TestSeeder.seedPostedPurchase(
                db = database,
                repo = purchaseRepository,
                restaurantId = restaurantId,
                ingredientId = ingredientId,
                areaId = areaId,
                unitOptionId = optionId,
                quantityEntered = BigDecimal("100"),
                unitCostBase = BigDecimal("5"),
                effectiveAt = java.time.Instant.now().minusSeconds(3600)
            )

            // Seed Active Recipe
            // Standard yield = 10, component quantity = 12
            database.preparationRecipeDao().insert(PreparationRecipeEntity(
                id = "rec1", restaurantId = restaurantId.value, outputIngredientId = outputIngredientId.value,
                name = "Grounding", normalizedName = "grounding",
                standardYieldQuantity = BigDecimal("10"), standardYieldQuantityBase = BigDecimal("10"),
                yieldUnitOptionId = outputOptionId.value, status = "ACTIVE", notes = null, createdAt = 0, updatedAt = 0, archivedAt = null
            ))
            database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity(
                id = "comp1", recipeId = "rec1", componentIngredientId = ingredientId.value,
                quantityEntered = BigDecimal("12"), quantityBase = BigDecimal("12"),
                unitOptionId = optionId.value, sortOrder = 0, notes = null, createdAt = 0, updatedAt = 0
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
            waitForTag("production_batch_list_screen")

            // 2. Create Draft
            composeTestRule.onNodeWithTag("add_production_batch_fab").performClick()
            waitForTag("production_batch_create_screen")
            
            composeTestRule.onNodeWithTag("production_recipe_selector").performClick()
            composeTestRule.onNodeWithText("Grounding").performClick()
            
            // Batch multiplier = 2
            // Expected component consumption = 24
            // Expected output = 20
            composeTestRule.onNodeWithTag("production_multiplier_field").performTextReplacement("2")
            composeTestRule.onNodeWithTag("production_output_area_selector").performClick()
            composeTestRule.onNodeWithText("Kitchen").performClick()
            
            composeTestRule.onNodeWithTag("production_batch_create").performClick()
            waitForTag("production_batch_draft_screen")

            // 3. Verify Draft state
            val batchSummaries = runBlocking { database.productionBatchDao().observeSummaries(restaurantId.value, null).first() }
            val summary = batchSummaries.firstOrNull()
            assertNotNull(summary)
            assertEquals("DRAFT", summary!!.status)
            
            val batchId = ProductionBatchId(summary.id)
            val batch = runBlocking { database.productionBatchDao().getById(batchId.value) }
            assertNotNull(batch)
            assertEquals(0, BigDecimal("2").compareTo(BigDecimal(batch!!.batchMultiplier)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.expectedOutputQuantityEntered)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.actualOutputQuantityEntered)))
            
            // 4. Open Component and Set Area
            val batchComponents = runBlocking { database.productionBatchDao().getComponents(batchId.value) }
            val component = batchComponents.first()
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(component.expectedQuantityEntered)))
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(component.actualQuantityEntered)))

            composeTestRule.onNodeWithTag("production_component_item_${component.id}").performClick()
            waitForTag("production_batch_component_screen")
            
            composeTestRule.onNodeWithTag("production_component_area_selector").performClick()
            composeTestRule.onNodeWithText("Kitchen").performClick()
            composeTestRule.onNodeWithTag("production_batch_save").performClick()
            waitForTag("production_batch_draft_screen")

            // 5. Review and Post
            composeTestRule.onNodeWithTag("production_batch_review").performClick()
            waitForTag("production_batch_preview_screen")
            
            // Verify Preview Costing
            // Component total cost = 24 * 5 = 120
            // Output unit cost = 120 / 20 = 6
            composeTestRule.onNodeWithText("120.00").assertExists()
            composeTestRule.onNodeWithText("6.00").assertExists()
            
            composeTestRule.onNodeWithTag("production_batch_post").performClick()
            composeTestRule.onNodeWithTag("production_post_confirmation").performClick()
            waitForTag("production_batch_detail_screen")

            // 6. Verify Posted Detail
            composeTestRule.onNodeWithTag("production_batch_detail_screen").assertExists()
            
            // Check movements
            val movements = runBlocking { 
                database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value) 
            }
            assertEquals(2, movements.size)
            
            val consumption = movements.find { it.movementType == "PRODUCTION_CONSUMPTION" }!!
            val output = movements.find { it.movementType == "PRODUCTION_OUTPUT" }!!
            
            assertEquals(0, BigDecimal("-24").compareTo(BigDecimal(consumption.quantityBaseSigned)))
            assertEquals(0, BigDecimal("5").compareTo(BigDecimal(consumption.unitCostBaseSnapshot!!)))
            assertEquals(0, BigDecimal("-120").compareTo(BigDecimal(consumption.totalValueSnapshot!!)))
            
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(output.quantityBaseSigned)))
            assertEquals(0, BigDecimal("6").compareTo(BigDecimal(output.unitCostBaseSnapshot!!)))
            assertEquals(0, BigDecimal("120").compareTo(BigDecimal(output.totalValueSnapshot!!)))
            
            // 7. Void
            composeTestRule.onNodeWithTag("production_batch_void").performClick()
            composeTestRule.onNodeWithText("Confirm").performClick()
            waitForTag("production_batch_detail_screen") // Status will change to VOIDED
            
            // 8. Verify Voided state
            val finalBatch = runBlocking { database.productionBatchDao().getById(batchId.value) }
            assertEquals("VOIDED", finalBatch!!.status)
            assertNotNull(finalBatch.voidedAt)
            
            val allMovements = runBlocking { 
                database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value) 
            }
            assertEquals(4, allMovements.size) // 2 original + 2 reversals
            val reversals = allMovements.filter { it.movementType == "REVERSAL" }
            assertEquals(2, reversals.size)
        }
    }

    private fun waitForHome() {
        waitForTag("home_screen")
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
