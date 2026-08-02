package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
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
        database.ingredientCostProjectionDao().upsert(
            IngredientCostProjectionEntity(
                restaurantId = restId.value,
                ingredientId = componentIngredientId.value,
                averageUnitCostBase = "10.00",
                updatedAt = Instant.now().toEpochMilli()
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
        // Remove cost
        database.ingredientCostProjectionDao().deleteForIngredient(componentIngredientId.value)

        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.ComponentCostUnavailable)
    }

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
