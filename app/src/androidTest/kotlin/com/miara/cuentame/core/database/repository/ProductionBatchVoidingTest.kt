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
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
class ProductionBatchVoidingTest {

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
    fun void_success_createsReversalsAndRestoresProjections() = runBlocking {
        // 1. Post a batch
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal("2"), areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)

        // Verify posted state
        val posted = repository.getBatch(batchId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)?.quantityBase ?: "0", "-1")
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)?.quantityBase ?: "0", "4")

        // 2. Void the batch
        repository.void(batchId)

        val voided = repository.getBatch(batchId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)

        // Verify movements
        val allMovements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        assertThat(allMovements).hasSize(4) // 2 original + 2 reversals
        
        val reversals = allMovements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
        assertThat(reversals).hasSize(2)
        
        val consReversal = reversals.find { it.ingredientId == componentIngredientId.value }!!
        assertBigDecimalEquivalent(consReversal.quantityBaseSigned, "1")
        
        val outReversal = reversals.find { it.ingredientId == outputIngredientId.value }!!
        assertBigDecimalEquivalent(outReversal.quantityBaseSigned, "-4")

        // Verify projections restored
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)?.quantityBase ?: "0", "10") // 10 original - 1 consumed + 1 reversed
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)?.quantityBase ?: "0", "0")
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
