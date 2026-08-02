package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.repository.ProductionBatchRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
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
class ProductionFailureBoundaryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: ProductionBatchRepository

    @Inject
    lateinit var purchaseRepository: PurchaseRepository

    @Inject
    lateinit var failureBoundary: IntegrationFailureBoundary

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val recipeId = PreparationRecipeId("r1")
    private val compIngId = IngredientId(TestSeeder.ING_ID)
    private val outIngId = IngredientId("output-ing")
    private val areaId = InventoryAreaId(TestSeeder.AREA_ID)

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        testStateManager.resetAll()
        testStateManager.seedBaseline()
        seedRecipe()
        (failureBoundary as ConfigurableFailureBoundary).reset()
    }

    private suspend fun seedRecipe() {
        database.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                id = outIngId.value, restaurantId = restId.value, name = "Out", normalizedName = "out",
                categoryId = null, baseUnitId = TestSeeder.UNIT_ID, defaultAreaId = areaId.value,
                sku = null, notes = null, reorderPointBase = null, isActive = true,
                createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )
        database.ingredientUnitOptionDao().insert(
            com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(
                id = "out-opt", ingredientId = outIngId.value, displayName = "Unit", shortLabel = "u",
                standardUnitId = null, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = true,
                isDefaultPurchase = false, isActive = true, createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = recipeId.value, restaurantId = restId.value, outputIngredientId = outIngId.value,
                name = "Recipe", normalizedName = "recipe", standardYieldQuantity = BigDecimal.ONE,
                standardYieldQuantityBase = BigDecimal.ONE, yieldUnitOptionId = "out-opt",
                status = PreparationRecipeStatus.ACTIVE.name, notes = null, createdAt = 0L, updatedAt = 0L, archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "rc1", recipeId = recipeId.value, componentIngredientId = compIngId.value,
                unitOptionId = TestSeeder.OPTION_ID, quantityEntered = BigDecimal("0.5"),
                quantityBase = BigDecimal("0.5"), sortOrder = 0, notes = null, createdAt = 0L, updatedAt = 0L
            )
        )
        
        TestSeeder.seedPostedPurchase(
            db = database, repo = purchaseRepository, restaurantId = restId,
            ingredientId = compIngId, areaId = areaId, unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
            quantityEntered = BigDecimal("10"), unitCostBase = BigDecimal("10.00"),
            effectiveAt = Instant.parse("2026-01-01T10:00:00Z")
        )
    }

    @Test
    fun post_failsAfterSnapshots_rollsBackEverything() = runBlocking {
        testPostFailure(IntegrationFailurePoints.PRODUCTION_POST_AFTER_SNAPSHOTS)
    }

    @Test
    fun post_failsAfterConsumption_rollsBackEverything() = runBlocking {
        testPostFailure(IntegrationFailurePoints.PRODUCTION_POST_AFTER_CONSUMPTION)
    }

    @Test
    fun post_failsAfterOutput_rollsBackEverything() = runBlocking {
        testPostFailure(IntegrationFailurePoints.PRODUCTION_POST_AFTER_OUTPUT)
    }

    @Test
    fun post_failsAfterProjections_rollsBackEverything() = runBlocking {
        testPostFailure(IntegrationFailurePoints.PRODUCTION_POST_AFTER_PROJECTIONS)
    }

    @Test
    fun post_failsAfterMarkPosted_rollsBackEverything() = runBlocking {
        testPostFailure(IntegrationFailurePoints.PRODUCTION_POST_AFTER_MARK_POSTED)
    }

    @Test
    fun void_failsAfterReversals_rollsBackEverything() = runBlocking {
        testVoidFailure(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_REVERSALS)
    }

    @Test
    fun void_failsAfterProjections_rollsBackEverything() = runBlocking {
        testVoidFailure(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_PROJECTIONS)
    }

    @Test
    fun void_failsAfterMarkVoided_rollsBackEverything() = runBlocking {
        testVoidFailure(IntegrationFailurePoints.PRODUCTION_VOID_AFTER_MARK_VOIDED)
    }

    private suspend fun testPostFailure(point: String) {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.parse("2026-01-01T12:00:00Z"), null
        ))

        // Capture Draft state
        val beforeBatch = repository.getBatch(batchId)!!
        val beforeComponents = beforeBatch.components
        val beforeCompBalance = database.inventoryProjectionDao().getBalance(compIngId.value, areaId.value)
        val beforeCompCost = database.ingredientCostProjectionDao().getCost(compIngId.value)
        val beforeOutBalance = database.inventoryProjectionDao().getBalance(outIngId.value, areaId.value)
        val beforeOutCost = database.ingredientCostProjectionDao().getCost(outIngId.value)

        (failureBoundary as ConfigurableFailureBoundary).triggerOn(point)

        try {
            repository.post(batchId)
        } catch (_: ForcedFailureException) {
            // Expected
        }

        val afterBatch = repository.getBatch(batchId)!!
        assertThat(afterBatch).isEqualTo(beforeBatch)
        assertThat(afterBatch.components).isEqualTo(beforeComponents)

        val movements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        assertThat(movements).isEmpty()

        val afterCompBalance = database.inventoryProjectionDao().getBalance(compIngId.value, areaId.value)
        val afterCompCost = database.ingredientCostProjectionDao().getCost(compIngId.value)
        val afterOutBalance = database.inventoryProjectionDao().getBalance(outIngId.value, areaId.value)
        val afterOutCost = database.ingredientCostProjectionDao().getCost(outIngId.value)

        assertThat(afterCompBalance).isEqualTo(beforeCompBalance)
        assertThat(afterCompCost).isEqualTo(beforeCompCost)
        assertThat(afterOutBalance).isEqualTo(beforeOutBalance)
        assertThat(afterOutCost).isEqualTo(beforeOutCost)
    }

    private suspend fun testVoidFailure(point: String) {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.parse("2026-01-01T12:00:00Z"), null
        ))
        repository.post(batchId)
        
        // Capture Posted state
        val beforeBatch = repository.getBatch(batchId)!!
        val beforeComponents = beforeBatch.components
        val beforeMovements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        val beforeCompBalance = database.inventoryProjectionDao().getBalance(compIngId.value, areaId.value)
        val beforeCompCost = database.ingredientCostProjectionDao().getCost(compIngId.value)
        val beforeOutBalance = database.inventoryProjectionDao().getBalance(outIngId.value, areaId.value)
        val beforeOutCost = database.ingredientCostProjectionDao().getCost(outIngId.value)

        (failureBoundary as ConfigurableFailureBoundary).triggerOn(point)

        try {
            repository.void(batchId)
        } catch (_: ForcedFailureException) {
            // Expected
        }

        val afterBatch = repository.getBatch(batchId)!!
        assertThat(afterBatch).isEqualTo(beforeBatch)
        assertThat(afterBatch.components).isEqualTo(beforeComponents)
        
        val afterMovements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        assertThat(afterMovements).isEqualTo(beforeMovements)
        assertThat(afterMovements.any { it.movementType == InventoryMovementType.REVERSAL.name }).isFalse()
        
        val afterCompBalance = database.inventoryProjectionDao().getBalance(compIngId.value, areaId.value)
        val afterCompCost = database.ingredientCostProjectionDao().getCost(compIngId.value)
        val afterOutBalance = database.inventoryProjectionDao().getBalance(outIngId.value, areaId.value)
        val afterOutCost = database.ingredientCostProjectionDao().getCost(outIngId.value)

        assertThat(afterCompBalance).isEqualTo(beforeCompBalance)
        assertThat(afterCompCost).isEqualTo(beforeCompCost)
        assertThat(afterOutBalance).isEqualTo(beforeOutBalance)
        assertThat(afterOutCost).isEqualTo(beforeOutCost)
    }
}
