package com.venkoi.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.venkoi.cuentame.core.database.entity.PreparationRecipeEntity
import com.venkoi.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationException
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import com.venkoi.cuentame.core.model.inventory.ProductionBatch
import com.venkoi.cuentame.core.model.inventory.ProductionBatchComponent
import com.venkoi.cuentame.test.TestSeeder
import com.venkoi.cuentame.test.TestStateManager
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
    lateinit var purchaseRepository: RoomPurchaseRepository

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
            com.venkoi.cuentame.core.database.entity.IngredientEntity(
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
            com.venkoi.cuentame.core.database.entity.IngredientUnitOptionEntity(
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
        TestSeeder.seedPostedPurchase(
            db = database,
            repo = purchaseRepository,
            restaurantId = restId,
            ingredientId = componentIngredientId,
            areaId = areaId,
            unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
            quantityEntered = BigDecimal("10"),
            unitCostBase = BigDecimal("10.00"),
            effectiveAt = Instant.parse("2026-01-01T10:00:00Z")
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

        // Capture beforeCall including rogue movement
        val snapshotBefore = captureProductionSnapshot(batchId)

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).containsExactly(ProductionBatchValidationFailure.MovementHistoryConflict)
        
        val snapshotAfter = captureProductionSnapshot(batchId)
        assertThat(snapshotAfter.batch).isEqualTo(snapshotBefore.batch)
        assertThat(snapshotAfter.components).isEqualTo(snapshotBefore.components)
        assertThat(snapshotAfter.movements).isEqualTo(snapshotBefore.movements)
        assertThat(snapshotAfter.balanceProjections).isEqualTo(snapshotBefore.balanceProjections)
        assertThat(snapshotAfter.costProjections).isEqualTo(snapshotBefore.costProjections)
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

        // Capture corruptedBeforeCall
        val snapshotBefore = captureProductionSnapshot(batchId)

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.post(batchId) }
        }
        assertThat(exception.failures).containsExactly(ProductionBatchValidationFailure.MovementHistoryConflict)
        
        val snapshotAfter = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshotBefore, snapshotAfter)
    }

    @Test
    fun voidPosted_withCorruptedHistory_returnsMovementHistoryConflict() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.post(batchId)
        
        val moves = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
        corruptMovement(moves.first().id, "quantityBaseSigned", "-999")

        // Capture corruptedBeforeCall
        val snapshotBefore = captureProductionSnapshot(batchId)

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.void(batchId) }
        }
        assertThat(exception.failures).containsExactly(ProductionBatchValidationFailure.MovementHistoryConflict)
        
        val snapshotAfter = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshotBefore, snapshotAfter)
        
        val batch = repository.getBatch(batchId)!!
        assertThat(batch.status).isEqualTo(com.venkoi.cuentame.core.model.inventory.DocumentStatus.POSTED)
        assertThat(batch.voidedAt).isNull()
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

        // Capture corruptedBeforeCall
        val snapshotBefore = captureProductionSnapshot(batchId)

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.void(batchId) }
        }
        assertThat(exception.failures).containsExactly(ProductionBatchValidationFailure.MovementHistoryConflict)
        
        val snapshotAfter = captureProductionSnapshot(batchId)
        assertSnapshotsEqual(snapshotBefore, snapshotAfter)
    }

    private data class ProductionSnapshot(
        val batch: ProductionBatch,
        val components: List<ProductionBatchComponent>,
        val movements: List<InventoryMovementEntity>,
        val balanceProjections: List<com.venkoi.cuentame.core.database.entity.InventoryBalanceProjectionEntity>,
        val costProjections: List<com.venkoi.cuentame.core.database.entity.IngredientCostProjectionEntity>
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
