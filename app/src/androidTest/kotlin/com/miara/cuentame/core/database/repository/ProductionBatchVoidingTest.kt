package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import com.miara.cuentame.test.PostedPurchaseFixture
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
    lateinit var purchaseRepository: PurchaseRepository

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
        val scenarioTime = Instant.parse("2026-01-01T10:00:00Z")
        TestSeeder.seedPostedPurchase(
            db = database,
            repo = purchaseRepository,
            restaurantId = restId,
            ingredientId = componentIngredientId,
            areaId = areaId,
            unitOptionId = optionId,
            quantityEntered = BigDecimal("10"),
            unitCostBase = BigDecimal("10.00"),
            effectiveAt = scenarioTime
        )
    }

    @Test
    fun void_success_createsReversalsAndRestoresProjections() = runBlocking {
        // Initial component balance: 10
        // Component average cost: 10.00
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)?.quantityBase ?: "0", "10")
        assertBigDecimalEquivalent(database.ingredientCostProjectionDao().getCost(componentIngredientId.value)?.averageUnitCostBase ?: "0", "10.00")
        assertThat(database.ingredientCostProjectionDao().getCost(outputIngredientId.value)).isNull()

        // 1. Post a batch
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal("2"), areaId, null, null, Instant.parse("2026-01-01T12:00:00Z"), null
        ))
        repository.post(batchId)

        // Verify posted state
        // Production consumption: 0.5 lb * 2 = 1 lb.
        // Component balance: 10 - 1 = 9.
        // Output production: 1 container * 2 = 2 containers = 4 lb base.
        val posted = repository.getBatch(batchId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)?.quantityBase ?: "0", "9")
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)?.quantityBase ?: "0", "4")
        
        // Component cost should remain unchanged
        assertBigDecimalEquivalent(database.ingredientCostProjectionDao().getCost(componentIngredientId.value)?.averageUnitCostBase ?: "0", "10.00")
        
        // Output cost: 1 lb @ $10.00 = $10.00. Output is 4 lb base. Unit cost = $10 / 4 = $2.50.
        assertBigDecimalEquivalent(database.ingredientCostProjectionDao().getCost(outputIngredientId.value)?.averageUnitCostBase ?: "0", "2.50")

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
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(componentIngredientId.value, areaId.value)?.quantityBase ?: "0", "10")
        assertBigDecimalEquivalent(database.inventoryProjectionDao().getBalance(outputIngredientId.value, areaId.value)?.quantityBase ?: "0", "0")
        
        assertBigDecimalEquivalent(database.ingredientCostProjectionDao().getCost(componentIngredientId.value)?.averageUnitCostBase ?: "0", "10.00")
        assertThat(database.ingredientCostProjectionDao().getCost(outputIngredientId.value)).isNull()
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
