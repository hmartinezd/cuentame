package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class RoomProductionBatchRepositoryFailureTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomProductionBatchRepository

    @Inject
    lateinit var failureBoundary: ConfigurableFailureBoundary

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val recipeId = PreparationRecipeId("recipe-1")
    private val componentIngredientId = IngredientId(TestSeeder.ING_ID)
    private val outputIngredientId = IngredientId("output-ing-1")
    private val areaId = InventoryAreaId(TestSeeder.AREA_ID)

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        testStateManager.resetAll()
        testStateManager.seedBaseline()
        seedOutputIngredient()
        seedRecipe()
        seedPostedPurchase()
    }

    @After
    fun tearDown() {
        failureBoundary.reset()
        database.close()
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
                displayName = "ct",
                shortLabel = "ct",
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

    private suspend fun seedRecipe() {
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = recipeId.value,
                restaurantId = restId.value,
                outputIngredientId = outputIngredientId.value,
                name = "Test Recipe",
                normalizedName = "test recipe",
                standardYieldQuantity = BigDecimal("1"),
                standardYieldQuantityBase = BigDecimal("1"),
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
                unitOptionId = TestSeeder.OPTION_ID,
                quantityEntered = BigDecimal("1"),
                quantityBase = BigDecimal("1"),
                sortOrder = 0,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    private suspend fun seedPostedPurchase() {
        val receiptId = "p1"
        val lineId = "pl1"
        database.purchaseDao().insertReceipt(
            PurchaseReceiptEntity(receiptId, restId.value, null, "INV1", 0, "POSTED", null, null, 0, 0, 0, null)
        )
        database.purchaseDao().insertLine(
            PurchaseLineEntity(lineId, receiptId, componentIngredientId.value, areaId.value, TestSeeder.OPTION_ID, "10", "10", "100", "10", null, 0, 0)
        )
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "m-purchase",
                restaurantId = restId.value,
                ingredientId = componentIngredientId.value,
                areaId = areaId.value,
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10.00",
                unitCostBaseSnapshot = "10.00",
                totalValueSnapshot = "100.00",
                effectiveAt = 0L,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = receiptId,
                sourceOperationId = "purchase-post:$receiptId:$lineId",
                sourceLineId = lineId,
                reversalOfMovementId = null,
                createdAt = 0L
            )
        )
        database.ingredientCostProjectionDao().upsert(
            com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity(restId.value, componentIngredientId.value, "10.00", 0L)
        )
        database.inventoryProjectionDao().upsert(
            com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity(restId.value, componentIngredientId.value, areaId.value, "10.00", 0L)
        )
    }

    private fun corruptMovement(id: String, field: String, value: String?) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE inventory_movements SET $field = ? WHERE id = ?",
            arrayOf(value, id)
        )
    }

    @Test
    fun postDraft_withUnexpectedMovement_returnsMovementHistoryConflict() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "m-rogue",
                restaurantId = restId.value,
                ingredientId = componentIngredientId.value,
                areaId = areaId.value,
                movementType = InventoryMovementType.MANUAL_ADJUSTMENT.name,
                quantityBaseSigned = "-1",
                unitCostBaseSnapshot = null,
                totalValueSnapshot = null,
                effectiveAt = Instant.now().toEpochMilli(),
                sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
                sourceDocumentId = batchId.value,
                sourceOperationId = "rogue",
                sourceLineId = null,
                reversalOfMovementId = null,
                createdAt = Instant.now().toEpochMilli()
            )
        )

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.MovementHistoryConflict)
    }

    @Test
    fun postPosted_withCorruptedMovement_returnsMovementHistoryConflict() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)
        
        val moves = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        val move = moves.first()
        corruptMovement(move.id, "quantityBaseSigned", "-999")

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.MovementHistoryConflict)
    }

    @Test
    fun voidPosted_withCorruptedHistory_returnsMovementHistoryConflict() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)
        
        val moves = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        corruptMovement(moves.first().id, "quantityBaseSigned", "-999")

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.void(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.MovementHistoryConflict)
    }

    @Test
    fun voidVoided_withCorruptedReversal_returnsMovementHistoryConflict() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)
        repository.void(batchId)

        val moves = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        val reversal = moves.find { it.movementType == InventoryMovementType.REVERSAL.name }!!
        corruptMovement(reversal.id, "quantityBaseSigned", "999")

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.void(batchId) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.MovementHistoryConflict)
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

    private suspend fun testVoidFailure(failurePoint: String) {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)
        
        val snapshot = captureProductionSnapshot(batchId)
        
        failureBoundary.triggerOn(failurePoint)
        
        assertThrows(RuntimeException::class.java) {
            runBlocking { repository.void(batchId) }
        }
        
        val finalSnapshot = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshot, finalSnapshot)
        
        val batch = repository.getBatch(batchId)!!
        assertThat(batch.status.name).isEqualTo("POSTED")
        assertThat(batch.voidedAt).isNull()
        
        val moves = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        assertThat(moves.any { it.movementType == InventoryMovementType.REVERSAL.name }).isFalse()
    }

    private data class ProductionSnapshot(
        val batch: ProductionBatch,
        val components: List<ProductionBatchComponent>,
        val movements: List<InventoryMovementEntity>,
        val balanceProjections: List<com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity>,
        val costProjections: List<com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity>
    )

    private suspend fun captureProductionSnapshot(batchId: ProductionBatchId): ProductionSnapshot {
        val batch = repository.getBatch(batchId)!!
        val components = batch.components
        val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        val balanceProjections = database.inventoryProjectionDao().getAll()
        val costProjections = database.ingredientCostProjectionDao().getAll()
        
        return ProductionSnapshot(batch, components, movements, balanceProjections, costProjections)
    }

    private fun assertSnapshotsEqual(s1: ProductionSnapshot, s2: ProductionSnapshot) {
        assertThat(s1.batch).isEqualTo(s2.batch)
        assertThat(s1.components).isEqualTo(s2.components)
        assertThat(s1.movements).isEqualTo(s2.movements)
        assertThat(s1.balanceProjections).isEqualTo(s2.balanceProjections)
        assertThat(s1.costProjections).isEqualTo(s2.costProjections)
    }
}
