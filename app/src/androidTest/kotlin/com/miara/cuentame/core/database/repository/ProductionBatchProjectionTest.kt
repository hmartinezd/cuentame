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
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductionBatchProjectionTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomProductionBatchRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val areaId = InventoryAreaId(TestSeeder.AREA_ID)

    // Ingredients
    private val rawIngId = IngredientId(TestSeeder.ING_ID)
    private val intermediateIngId = IngredientId("intermediate-ing")
    private val finalIngId = IngredientId("final-ing")

    // Recipes
    private val intermediateRecipeId = PreparationRecipeId("recipe-intermediate")
    private val finalRecipeId = PreparationRecipeId("recipe-final")

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        testStateManager.resetAll()
        testStateManager.seedBaseline()
        
        seedIntermediateIngredient()
        seedFinalIngredient()
        seedRecipes()
        seedRawCost()
    }

    private suspend fun seedIntermediateIngredient() {
        database.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                id = intermediateIngId.value,
                restaurantId = restId.value,
                name = "Intermediate",
                normalizedName = "intermediate",
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
                id = "opt-intermediate",
                ingredientId = intermediateIngId.value,
                displayName = "lb",
                shortLabel = "lb",
                standardUnitId = null,
                factorToBase = BigDecimal.ONE,
                isBase = true,
                isDefaultCount = true,
                isDefaultPurchase = false,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
    }

    private suspend fun seedFinalIngredient() {
        database.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                id = finalIngId.value,
                restaurantId = restId.value,
                name = "Final",
                normalizedName = "final",
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
                id = "opt-final",
                ingredientId = finalIngId.value,
                displayName = "lb",
                shortLabel = "lb",
                standardUnitId = null,
                factorToBase = BigDecimal.ONE,
                isBase = true,
                isDefaultCount = true,
                isDefaultPurchase = false,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
    }

    private suspend fun seedRecipes() {
        // Recipe: Intermediate = 2 * Raw
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = intermediateRecipeId.value,
                restaurantId = restId.value,
                outputIngredientId = intermediateIngId.value,
                name = "Intermediate Recipe",
                normalizedName = "intermediate recipe",
                standardYieldQuantity = BigDecimal("1"),
                standardYieldQuantityBase = BigDecimal("1"),
                yieldUnitOptionId = "opt-intermediate",
                status = PreparationRecipeStatus.ACTIVE.name,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L,
                archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "comp-int-1",
                recipeId = intermediateRecipeId.value,
                componentIngredientId = rawIngId.value,
                unitOptionId = TestSeeder.OPTION_ID,
                quantityEntered = BigDecimal("2"),
                quantityBase = BigDecimal("2"),
                sortOrder = 0,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        // Recipe: Final = 0.5 * Intermediate
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = finalRecipeId.value,
                restaurantId = restId.value,
                outputIngredientId = finalIngId.value,
                name = "Final Recipe",
                normalizedName = "final recipe",
                standardYieldQuantity = BigDecimal("1"),
                standardYieldQuantityBase = BigDecimal("1"),
                yieldUnitOptionId = "opt-final",
                status = PreparationRecipeStatus.ACTIVE.name,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L,
                archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "comp-final-1",
                recipeId = finalRecipeId.value,
                componentIngredientId = intermediateIngId.value,
                unitOptionId = "opt-intermediate",
                quantityEntered = BigDecimal("0.5"),
                quantityBase = BigDecimal("0.5"),
                sortOrder = 0,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    private suspend fun seedRawCost() {
        val now = Instant.now().toEpochMilli()
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "move-raw-seed",
                restaurantId = restId.value,
                ingredientId = rawIngId.value,
                areaId = areaId.value,
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10.00",
                unitCostBaseSnapshot = "10.00",
                totalValueSnapshot = "100.00",
                effectiveAt = now - 10000,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "receipt-seed",
                sourceOperationId = "seed-raw",
                sourceLineId = "line-seed",
                reversalOfMovementId = null,
                createdAt = now - 10000
            )
        )
        database.ingredientCostProjectionDao().upsert(
            IngredientCostProjectionEntity(
                restaurantId = restId.value,
                ingredientId = rawIngId.value,
                averageUnitCostBase = "10.00",
                updatedAt = now - 10000
            )
        )
    }

    @Test
    fun nestedProduction_calculatesCostsCorrectly() = runBlocking {
        // 1. Produce Intermediate
        // Intermediate = 1 * (2 * Raw)
        // Raw Cost = 10.00. Total Cost = 2 * 10.00 = 20.00
        // Intermediate Output = 1. Intermediate Unit Cost = 20.00
        val intBatchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, intermediateRecipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(intBatchId)

        val intBatch = repository.getBatch(intBatchId)
        assertBigDecimalEquivalent(intBatch?.outputUnitCostBaseSnapshot ?: BigDecimal.ZERO, "20.00")
        
        // Verify Intermediate Cost Projection
        val intCostProj = database.ingredientCostProjectionDao().getCost(intermediateIngId.value)
        assertBigDecimalEquivalent(intCostProj?.averageUnitCostBase ?: "0", "20.00")

        // 2. Produce Final
        // Final = 1 * (0.5 * Intermediate)
        // Intermediate Cost = 20.00. Total Cost = 0.5 * 20.00 = 10.00
        // Final Output = 1. Final Unit Cost = 10.00
        val finalBatchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, finalRecipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(finalBatchId)

        val finalBatch = repository.getBatch(finalBatchId)
        assertBigDecimalEquivalent(finalBatch?.outputUnitCostBaseSnapshot ?: BigDecimal.ZERO, "10.00")

        // Verify Final Cost Projection
        val finalCostProj = database.ingredientCostProjectionDao().getCost(finalIngId.value)
        assertBigDecimalEquivalent(finalCostProj?.averageUnitCostBase ?: "0", "10.00")
    }

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
