package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductionBatchPostingTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomProductionBatchRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val recipeId = PreparationRecipeId("recipe-1")
    private val componentIngredientId = IngredientId(TestSeeder.ING_ID)
    private val outputIngredientId = IngredientId("output-ing-1")
    private val areaId = InventoryAreaId(TestSeeder.AREA_ID)
    private val optionId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        testStateManager.resetAll()
        testStateManager.seedBaseline()
        seedOutputIngredient()
        seedRecipe()
        seedCost()
    }

    private suspend fun seedOutputIngredient() {
        database.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                id = outputIngredientId.value,
                restaurantId = restId.value,
                name = "Prepared Salad",
                normalizedName = "prepared salad",
                categoryId = null,
                baseUnitId = TestSeeder.UNIT_ID,
                defaultAreaId = areaId.value,
                sku = null,
                notes = null,
                reorderPointBase = null,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
        database.ingredientUnitOptionDao().insert(
            com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(
                id = "output-opt-1",
                ingredientId = outputIngredientId.value,
                displayName = "Container",
                shortLabel = "ct",
                standardUnitId = null,
                factorToBase = BigDecimal("2"),
                isBase = false,
                isDefaultCount = true,
                isDefaultPurchase = false,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
    }

    private suspend fun seedRecipe() {
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = recipeId.value,
                restaurantId = restId.value,
                outputIngredientId = outputIngredientId.value,
                name = "Test Recipe",
                normalizedName = "test recipe",
                standardYieldQuantity = BigDecimal("1"),
                standardYieldQuantityBase = BigDecimal("2"),
                yieldUnitOptionId = "output-opt-1",
                status = PreparationRecipeStatus.ACTIVE.name,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L,
                archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "comp-1",
                recipeId = recipeId.value,
                componentIngredientId = componentIngredientId.value,
                unitOptionId = optionId.value,
                quantityEntered = BigDecimal("0.5"),
                quantityBase = BigDecimal("0.5"),
                sortOrder = 0,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    private suspend fun seedCost() {
        val now = Instant.now().toEpochMilli()
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "move-seed-1",
                restaurantId = restId.value,
                ingredientId = componentIngredientId.value,
                areaId = areaId.value,
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10.00",
                unitCostBaseSnapshot = "10.00",
                totalValueSnapshot = "100.00",
                effectiveAt = now - 10000,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "receipt-seed-1",
                sourceOperationId = "seed-op-1",
                sourceLineId = "line-seed-1",
                reversalOfMovementId = null,
                createdAt = now - 10000
            )
        )
        // Also seed projection so it exists for other queries if any
        database.ingredientCostProjectionDao().upsert(
            IngredientCostProjectionEntity(
                restaurantId = restId.value,
                ingredientId = componentIngredientId.value,
                averageUnitCostBase = "10.00",
                updatedAt = now - 10000
            )
        )
    }

    @Test
    fun post_success_createsMovementsAndCalculatesCosts() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restaurantId = restId,
            recipeId = recipeId,
            batchMultiplier = BigDecimal("2"),
            outputAreaId = areaId,
            actualOutputQuantityEntered = null,
            outputUnitOptionId = null,
            effectiveAt = Instant.now(),
            notes = null
        ))

        repository.post(batchId)

        val posted = repository.getBatch(batchId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)

        // Cost Calculation:
        // Component: 0.5 (recipe) * 2 (multiplier) = 1.0 lb
        // Unit Cost: 10.00
        // Total Comp Cost: 1.0 * 10.00 = 10.00
        // Output Qty: 1 (recipe) * 2 (multiplier) = 2 containers = 4 lb
        // Output Unit Cost: 10.00 / 4 = 2.50 per lb
        
        assertBigDecimalEquivalent(posted?.totalComponentCostSnapshot ?: BigDecimal.ZERO, "10.00")
        assertBigDecimalEquivalent(posted?.outputUnitCostBaseSnapshot ?: BigDecimal.ZERO, "2.50")

        val movements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        assertThat(movements).hasSize(2) // 1 consumption + 1 output

        val consumption = movements.find { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }!!
        assertBigDecimalEquivalent(consumption.quantityBaseSigned, "-1")
        assertBigDecimalEquivalent(consumption.unitCostBaseSnapshot ?: "0", "10.00")
        assertBigDecimalEquivalent(consumption.totalValueSnapshot ?: "0", "-10.00")

        val output = movements.find { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }!!
        assertBigDecimalEquivalent(output.quantityBaseSigned, "4")
        assertBigDecimalEquivalent(output.unitCostBaseSnapshot ?: "0", "2.50")
        assertBigDecimalEquivalent(output.totalValueSnapshot ?: "0", "10.00")

        // Verify projections
        val compProj = database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)
        assertBigDecimalEquivalent(compProj?.quantityBase ?: "0", "-1")

        val outProj = database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)
        assertBigDecimalEquivalent(outProj?.quantityBase ?: "0", "4")

        val outCostProj = database.ingredientCostProjectionDao().getCost(outputIngredientId.value)
        assertBigDecimalEquivalent(outCostProj?.averageUnitCostBase ?: "0", "2.50")
    }

    @Test
    fun post_fails_whenCostUnavailable() = runBlocking {
        // Remove cost movements
        database.inventoryMovementDao().deleteAll()
        database.ingredientCostProjectionDao().deleteForIngredient(componentIngredientId.value)

        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.ComponentCostUnavailable)
    }

    @Test
    fun nestedProduction_calculatesCostCorrectly() = runBlocking {
        // Raw -> Intermediate -> Final
        // Raw: $10/lb, 10 lb available
        // Intermediate Recipe: Consumes 2 lb Raw -> Produces 1 unit (2 lb base) Intermediate
        // Intermediate Production: Output cost = 2 * $10 = $20 total = $10/lb
        // Final Recipe: Consumes 0.5 unit (1 lb base) Intermediate -> Produces 1 unit Final
        // Final Production: Output cost = 1 * $10 = $10 total

        val rawIngId = componentIngredientId
        val intermediateIngId = outputIngredientId // Already seeded container=2lb
        val finalIngId = IngredientId("final-ing")

        // Seed Final Ingredient
        database.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                id = finalIngId.value, restaurantId = restId.value, name = "Final", normalizedName = "final",
                categoryId = null, baseUnitId = TestSeeder.UNIT_ID, defaultAreaId = areaId.value,
                sku = null, notes = null, reorderPointBase = null, isActive = true,
                createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )
        database.ingredientUnitOptionDao().insert(
            com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(
                id = "final-opt", ingredientId = finalIngId.value, displayName = "Each", shortLabel = "ea",
                standardUnitId = null, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
                isDefaultPurchase = false, isActive = true, createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )

        // Intermediate Recipe already seeded (Intermediate consumes Raw)
        // Now seed Final Recipe (Final consumes Intermediate)
        val finalRecipeId = PreparationRecipeId("final-recipe")
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = finalRecipeId.value, restaurantId = restId.value, outputIngredientId = finalIngId.value,
                name = "Final Recipe", normalizedName = "final recipe",
                standardYieldQuantity = BigDecimal.ONE, standardYieldQuantityBase = BigDecimal.ONE,
                yieldUnitOptionId = "final-opt", status = PreparationRecipeStatus.ACTIVE.name,
                notes = null, createdAt = 0L, updatedAt = 0L, archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "comp-final-1", recipeId = finalRecipeId.value,
                componentIngredientId = intermediateIngId.value, unitOptionId = "output-opt-1",
                quantityEntered = BigDecimal("0.5"), quantityBase = BigDecimal.ONE, // 0.5 container = 1 lb
                sortOrder = 0, notes = null, createdAt = 0L, updatedAt = 0L
            )
        )

        // 1. Post Intermediate Production
        val intBatchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(intBatchId)

        val intBatch = repository.getBatch(intBatchId)!!
        assertBigDecimalEquivalent(intBatch.outputUnitCostBaseSnapshot ?: BigDecimal.ZERO, "5.00") // 0.5lb Raw @ $10 = $5. Output is 2lb. $5/2lb = $2.5/lb. 
        // Wait, Raw = $10/lb. Comp consumes 0.5lb Raw = $5. Output is 2lb base. Unit cost = $5 / 2 = $2.5/lb.
        // My manual calculation in the test comment was different, let's stick to the numbers.
        // Recipe comp: 0.5 lb Raw. 1x multiplier = 0.5 lb Raw. $10/lb -> $5 cost.
        // Recipe yield: 1 container = 2 lb.
        // Output Unit Cost = $5 / 2 lb = $2.5/lb.

        // 2. Post Final Production
        val finalBatchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, finalRecipeId, BigDecimal.ONE, areaId, null, null, Instant.now().plusSeconds(60), null
        ))
        repository.post(finalBatchId)

        val finalBatch = repository.getBatch(finalBatchId)!!
        // Final consumes 0.5 container Intermediate = 1 lb base.
        // Intermediate cost = $2.5/lb.
        // Component cost = 1 lb * $2.5/lb = $2.5.
        // Final yield = 1 each = 1 lb.
        // Final Unit Cost = $2.5 / 1 lb = $2.5/lb.
        assertBigDecimalEquivalent(finalBatch.outputUnitCostBaseSnapshot ?: BigDecimal.ZERO, "2.50")
    }

    @Test
    fun preview_matches_postedResult() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal("3"), areaId, null, null, Instant.now(), null
        ))

        val preview = repository.calculatePostingPreview(batchId)
        repository.post(batchId)
        val posted = repository.getBatch(batchId)!!

        assertThat(preview.totalComponentCost).isNotNull()
        assertBigDecimalEquivalent(preview.totalComponentCost!!, posted.totalComponentCostSnapshot!!)
        assertBigDecimalEquivalent(preview.outputUnitCostBase!!, posted.outputUnitCostBaseSnapshot!!)
        assertBigDecimalEquivalent(preview.actualOutputQuantityBase, posted.actualOutputQuantityBase)

        assertThat(preview.components).hasSize(posted.components.size)
        preview.components.forEachIndexed { index, pComp ->
            val bComp = posted.components[index]
            assertBigDecimalEquivalent(pComp.actualQuantityBase, bComp.actualQuantityBase)
            assertBigDecimalEquivalent(pComp.totalCost!!, bComp.totalCostSnapshot!!)
        }
    }

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: BigDecimal) {
        assertThat(actual.compareTo(expected)).isEqualTo(0)
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
