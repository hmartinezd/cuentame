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
        val snapshotBefore = captureProductionSnapshot(batchId)

        (failureBoundary as ConfigurableFailureBoundary).triggerOn(point)

        assertThrows(ForcedFailureException::class.java) {
            runBlocking { repository.post(batchId) }
        }

        val snapshotAfter = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshotBefore, snapshotAfter)

        val movements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value)
        assertThat(movements).isEmpty()
    }

    private suspend fun testVoidFailure(point: String) {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.parse("2026-01-01T12:00:00Z"), null
        ))
        repository.post(batchId)
        
        // Capture Posted state
        val snapshotBefore = captureProductionSnapshot(batchId)

        (failureBoundary as ConfigurableFailureBoundary).triggerOn(point)

        assertThrows(ForcedFailureException::class.java) {
            runBlocking { repository.void(batchId) }
        }

        val snapshotAfter = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshotBefore, snapshotAfter)
        
        assertThat(snapshotAfter.movements.any { it.movementType == InventoryMovementType.REVERSAL.name }).isFalse()
    }

    private data class ProductionSnapshot(
        val batch: com.miara.cuentame.core.model.inventory.ProductionBatch,
        val components: List<com.miara.cuentame.core.model.inventory.ProductionBatchComponent>,
        val movements: List<com.miara.cuentame.core.database.entity.InventoryMovementEntity>,
        val compBalance: com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity?,
        val compCost: com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity?,
        val outBalance: com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity?,
        val outCost: com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity?
    )

    private suspend fun captureProductionSnapshot(batchId: ProductionBatchId): ProductionSnapshot {
        val batch = repository.getBatch(batchId)!!
        return ProductionSnapshot(
            batch = batch,
            components = batch.components,
            movements = database.inventoryMovementDao().getBySourceDocument("PRODUCTION_BATCH", batchId.value),
            compBalance = database.inventoryProjectionDao().getBalance(compIngId.value, areaId.value),
            compCost = database.ingredientCostProjectionDao().getCost(compIngId.value),
            outBalance = database.inventoryProjectionDao().getBalance(outIngId.value, areaId.value),
            outCost = database.ingredientCostProjectionDao().getCost(outIngId.value)
        )
    }

    private fun assertSnapshotsEqual(s1: ProductionSnapshot, s2: ProductionSnapshot) {
        assertThat(s1.batch).isEqualTo(s2.batch)
        assertThat(s1.components).isEqualTo(s2.components)
        assertThat(s1.movements).isEqualTo(s2.movements)
        assertThat(s1.compBalance).isEqualTo(s2.compBalance)
        assertThat(s1.compCost).isEqualTo(s2.compCost)
        assertThat(s1.outBalance).isEqualTo(s2.outBalance)
        assertThat(s1.outCost).isEqualTo(s2.outCost)
    }
}
