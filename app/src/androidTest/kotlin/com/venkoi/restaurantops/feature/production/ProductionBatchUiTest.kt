package com.venkoi.restaurantops.feature.production

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.restaurantops.MainActivity
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.designsystem.util.Formatters
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import com.venkoi.restaurantops.test.TestSeeder
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementOperationIds
import com.venkoi.restaurantops.core.domain.repository.ProductionBatchRepository
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
import java.util.Locale
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

    @Inject
    lateinit var productionBatchRepository: ProductionBatchRepository

    @Inject
    lateinit var testStateManager: com.venkoi.restaurantops.test.TestStateManager

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
            testStateManager.resetAll()
            
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
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun complete_production_lifecycle_e2e() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHome()

            // 1. Open Production List from Home
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true).performScrollToNode(hasTestTag("open_production_batches_button"))
            composeTestRule.onNodeWithTag("open_production_batches_button").performClick()
            waitForTag("production_batch_list_screen")

            // 2. Create Draft
            composeTestRule.onNodeWithTag("add_production_batch_fab").performClick()
            waitForTag("production_batch_create_screen")
            
            composeTestRule.onNodeWithTag("production_recipe_selector").performClick()
            composeTestRule.onAllNodesWithText("Grounding").onLast().performClick()
            
            // Batch multiplier = 2
            // Expected component consumption = 24
            // Expected output = 20
            composeTestRule.onNodeWithTag("production_multiplier_field").performTextReplacement("2")
            composeTestRule.onNodeWithTag("production_output_area_selector").performClick()
            composeTestRule.onAllNodesWithText("Kitchen").onLast().performClick()
            composeTestRule.onNodeWithTag("production_output_unit_selector").performClick()
            composeTestRule.onAllNodesWithText("Unit").onLast().performClick()
            
            composeTestRule.onNodeWithTag("production_batch_create").performScrollTo().performClick()
            waitForTag("production_batch_draft_screen")

            // 3. Verify Draft state (Strengthened Draft assertions)
            val batchSummaries = runBlocking { database.productionBatchDao().observeSummaries(restaurantId.value, null).first() }
            val summary = batchSummaries.firstOrNull()
            assertNotNull(summary)
            
            val batchId = ProductionBatchId(summary!!.id)
            val batch = runBlocking { database.productionBatchDao().getById(batchId.value) }
            assertNotNull(batch)
            assertEquals("DRAFT", batch!!.status)
            assertEquals("rec1", batch.recipeId)
            assertEquals("Grounding", batch.recipeNameSnapshot)
            assertEquals(0, BigDecimal("2").compareTo(BigDecimal(batch.batchMultiplier)))
            assertEquals(0, BigDecimal("10").compareTo(BigDecimal(batch.recipeStandardYieldQuantitySnapshot)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.expectedOutputQuantityEntered)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.expectedOutputQuantityBase)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.actualOutputQuantityEntered)))
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(batch.actualOutputQuantityBase)))
            assertFalse(batch.hasManualOutputQuantityOverride)
            assertEquals("o-out1", batch.outputUnitOptionId)
            assertEquals("a1", batch.outputAreaId)
            assertNull(batch.totalComponentCostSnapshot)
            assertNull(batch.outputUnitCostBaseSnapshot)

            val components = runBlocking { database.productionBatchDao().getComponents(batchId.value) }
            assertEquals(1, components.size)
            val comp = components.first()
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(comp.expectedQuantityEntered)))
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(comp.expectedQuantityBase)))
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(comp.actualQuantityEntered)))
            assertEquals(0, BigDecimal("24").compareTo(BigDecimal(comp.actualQuantityBase)))
            assertFalse(comp.hasManualQuantityOverride)

            val initialMovements = runBlocking { database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value) }
            assertEquals(0, initialMovements.size)

            // 4. Open Component and Set Area
            composeTestRule.onNodeWithTag("production_batch_draft_list")
                .performScrollToNode(hasTestTag("production_component_item_${comp.id}"))
            composeTestRule.onNodeWithText("Raw Beef").performScrollTo().performClick()
            waitForTag("production_batch_component_screen")
            
            composeTestRule.onNodeWithTag("production_component_area_selector").performClick()
            composeTestRule.onAllNodesWithText("Kitchen").onLast().performClick()
            composeTestRule.onNodeWithTag("production_batch_save").performClick()
            waitForTag("production_batch_draft_screen")

            // 5. Review and Post (Strengthened Posting assertions)
            composeTestRule.onNodeWithTag("production_batch_review").performClick()
            waitForTag("production_batch_preview_screen")
            
            // Verify Preview Costing using repository directly for deterministic values
            val calculatedPreview = runBlocking { productionBatchRepository.calculatePostingPreview(batchId) }
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            val expectedTotalCostText = Formatters.formatCurrency(calculatedPreview.totalComponentCost!!, "USD", Locale.getDefault())
            val expectedUnitCostText = context.getString(R.string.production_currency_per_base, Formatters.formatCurrency(calculatedPreview.outputUnitCostBase!!, "USD", Locale.getDefault()))
            
            composeTestRule.onNode(
                hasText(expectedTotalCostText, substring = true) and
                    hasAnyAncestor(hasTestTag("production_preview_total_cost")),
                useUnmergedTree = true
            ).assertExists()
            composeTestRule.onNode(
                hasText(expectedUnitCostText, substring = true) and
                    hasAnyAncestor(hasTestTag("production_preview_output_unit_cost")),
                useUnmergedTree = true
            ).assertExists()
            
            composeTestRule.onNodeWithTag("production_batch_post").performClick()
            composeTestRule.onNodeWithTag("production_post_confirmation").performClick()
            
            // Deterministic wait for status posted
            waitForTag("production_status_posted")
            waitForTag("production_batch_detail_screen")

            // 6. Verify Posted state
            val postedBatch = runBlocking { database.productionBatchDao().getById(batchId.value) }
            assertNotNull(postedBatch)
            assertEquals("POSTED", postedBatch!!.status)
            assertNotNull(postedBatch.postedAt)
            assertNull(postedBatch.voidedAt)

            val movements = runBlocking { 
                database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value) 
            }
            assertEquals(2, movements.size)
            
            val consumption = movements.find { it.movementType == "PRODUCTION_CONSUMPTION" }!!
            val output = movements.find { it.movementType == "PRODUCTION_OUTPUT" }!!
            val reversalsCount = movements.count { it.movementType == "REVERSAL" }
            assertEquals(0, reversalsCount)

            // Verify canonical operation IDs
            assertEquals("production-post:${batchId.value}:consume:${comp.id}", consumption.sourceOperationId)
            assertEquals("production-post:${batchId.value}:output", output.sourceOperationId)
            
            // Verify Consumption
            assertEquals(0, BigDecimal("-24").compareTo(BigDecimal(consumption.quantityBaseSigned)))
            assertEquals(0, BigDecimal("5").compareTo(BigDecimal(consumption.unitCostBaseSnapshot!!)))
            assertEquals(0, BigDecimal("-120").compareTo(BigDecimal(consumption.totalValueSnapshot!!)))
            
            // Verify Output
            assertEquals(0, BigDecimal("20").compareTo(BigDecimal(output.quantityBaseSigned)))
            assertEquals(0, BigDecimal("6").compareTo(BigDecimal(output.unitCostBaseSnapshot!!)))
            assertEquals(0, BigDecimal("120").compareTo(BigDecimal(output.totalValueSnapshot!!)))
            
            // Verify cost conservation
            val totalMovementValue = movements.sumOf { BigDecimal(it.totalValueSnapshot ?: "0") }
            assertEquals(0, BigDecimal.ZERO.compareTo(totalMovementValue))

            // Verify current projections
            runBlocking {
                val rawProj = database.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)
                assertEquals(0, BigDecimal("76").compareTo(BigDecimal(rawProj!!.quantityBase)))
                
                val outProj = database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)
                assertEquals(0, BigDecimal("20").compareTo(BigDecimal(outProj!!.quantityBase)))

                val rawCost = database.ingredientCostProjectionDao().getCost(ingredientId.value)
                assertEquals(0, BigDecimal("5").compareTo(BigDecimal(rawCost!!.averageUnitCostBase!!)))
                
                val outCost = database.ingredientCostProjectionDao().getCost(outputIngredientId.value)
                assertEquals(0, BigDecimal("6").compareTo(BigDecimal(outCost!!.averageUnitCostBase!!)))
            }
            
            // 7. Void (Strengthened Void assertions)
            val originalMovements = movements.sortedBy { it.id }
            
            composeTestRule.onNodeWithTag("production_batch_void").performClick()
            composeTestRule.onNodeWithTag("production_void_confirm").performClick()
            
            // Deterministic wait for status voided
            waitForTag("production_status_voided")
            
            // 8. Verify Voided state
            val finalBatch = runBlocking { database.productionBatchDao().getById(batchId.value) }
            assertEquals("VOIDED", finalBatch!!.status)
            assertNotNull(finalBatch.voidedAt)
            
            val allMovements = runBlocking { 
                database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value) 
            }
            assertEquals(4, allMovements.size) // 2 original + 2 reversals
            
            // Original movements exactly unchanged
            originalMovements.forEach { original ->
                val current = allMovements.find { it.id == original.id }
                assertEquals(original, current)
            }
            
            val finalReversals = allMovements.filter { it.movementType == "REVERSAL" }
            assertEquals(2, finalReversals.size)
            
            // Each original has exactly one Reversal with identical non-negated properties
            originalMovements.forEach { original ->
                val reversal = finalReversals.find { it.reversalOfMovementId == original.id }
                assertNotNull(reversal)
                
                assertEquals(original.restaurantId, reversal!!.restaurantId)
                assertEquals(original.ingredientId, reversal.ingredientId)
                assertEquals(original.areaId, reversal.areaId)
                assertEquals(original.sourceDocumentType, reversal.sourceDocumentType)
                assertEquals(original.sourceDocumentId, reversal.sourceDocumentId)
                assertEquals(original.sourceLineId, reversal.sourceLineId)
                assertEquals(original.unitCostBaseSnapshot, reversal.unitCostBaseSnapshot)
                assertEquals(original.id, reversal.reversalOfMovementId)
                
                assertEquals(
                    InventoryMovementOperationIds.reversal(original.id),
                    reversal.sourceOperationId
                )

                assertEquals(0, BigDecimal(original.quantityBaseSigned).negate().compareTo(BigDecimal(reversal.quantityBaseSigned)))
                assertEquals(0, BigDecimal(original.totalValueSnapshot!!).negate().compareTo(BigDecimal(reversal.totalValueSnapshot!!)))
            }
            
            // Verify no duplicate Reversal operation IDs
            assertEquals(finalReversals.size, finalReversals.map { it.sourceOperationId }.distinct().size)

            // Verify final projections
            runBlocking {
                val rawProjFinal = database.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)
                assertEquals(0, BigDecimal("100").compareTo(BigDecimal(rawProjFinal!!.quantityBase)))

                val outProjFinal = database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)
                assertTrue("Output balance should be zero or absent", outProjFinal == null || BigDecimal.ZERO.compareTo(BigDecimal(outProjFinal.quantityBase)) == 0)

                val rawCostFinal = database.ingredientCostProjectionDao().getCost(ingredientId.value)
                assertEquals(0, BigDecimal("5").compareTo(BigDecimal(rawCostFinal!!.averageUnitCostBase!!)))
                
                val outCostFinal = database.ingredientCostProjectionDao().getCost(outputIngredientId.value)
                assertTrue("Output cost should be absent after reversal as no other history exists", outCostFinal == null || outCostFinal.averageUnitCostBase == null)
            }
        }
    }

    private fun waitForHome() {
        waitForTag("home_dashboard_list")
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
