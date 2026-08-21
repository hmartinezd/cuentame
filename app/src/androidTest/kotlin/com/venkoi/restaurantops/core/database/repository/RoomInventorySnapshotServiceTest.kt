package com.venkoi.restaurantops.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryMovementEntity
import com.venkoi.restaurantops.core.domain.service.HistoricalInventoryCostCalculator
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementOperationIds
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomInventorySnapshotServiceTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var service: RoomInventorySnapshotService
    private val restaurantId = RestaurantId("rest_1")
    private val ingredientId = IngredientId("ing_1")
    private val areaId = InventoryAreaId("area_1")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val inventoryValidator = InventoryMovementValidator()
        val historyValidator = InventoryMovementHistoryValidator(inventoryValidator)
        service = RoomInventorySnapshotService(
            db.inventoryMovementDao(),
            HistoricalInventoryCostCalculator(),
            historyValidator
        )

        runBlocking {
            db.restaurantDao().insert(com.venkoi.restaurantops.core.database.entity.RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null))
            db.unitDao().insertSeedUnits(listOf(com.venkoi.restaurantops.core.database.entity.UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)))
            db.inventoryAreaDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity(areaId.value, restaurantId.value, "Area 1", "area 1", 0, true, 0, 0, null))
            db.ingredientDao().insert(com.venkoi.restaurantops.core.database.entity.IngredientEntity(ingredientId.value, restaurantId.value, "Ing 1", "ing 1", null, "mass_lb", null, null, null, null, true, 0, 0, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun calculateAt_noHistory_returnsEmpty() = runBlocking {
        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, Instant.now())
        assertThat(snapshot.hasEffectiveHistory).isFalse()
        assertThat(snapshot.areaQuantityBase).isEqualTo(BigDecimal.ZERO)
        assertThat(snapshot.ingredientAverageCostBase).isNull()
    }

    @Test
    fun calculateAt_withHistory_returnsSnapshot() = runBlocking {
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        
        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, now)
        assertThat(snapshot.hasEffectiveHistory).isTrue()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase?.compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    @Test
    fun calculateAt_reversal_cancelsOriginal() = runBlocking {
        val now = Instant.now()
        val t1 = now.minusSeconds(200)
        val t2 = now.minusSeconds(100)
        
        val original = createMovement("m1", "PURCHASE", "10", "5", t1)
        val reversal = createExactReversal("m2", original, t2)
        
        db.inventoryMovementDao().insert(original)
        db.inventoryMovementDao().insert(reversal)

        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, now)
        assertThat(snapshot.hasEffectiveHistory).isFalse()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase).isNull()
    }

    @Test
    fun calculateAt_futureReversal_doesNotCancel() = runBlocking {
        val now = Instant.now()
        val t1 = now.minusSeconds(100)
        val t2 = now.plusSeconds(100)

        val original = createMovement("m1", "PURCHASE", "10", "5", t1)
        val reversal = createExactReversal("m2", original, t2)

        db.inventoryMovementDao().insert(original)
        db.inventoryMovementDao().insert(reversal)

        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, now)
        assertThat(snapshot.hasEffectiveHistory).isTrue()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase?.compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    @Test
    fun calculateAt_historyWithoutCost_returnsNullCost() = runBlocking {
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "OPENING_BALANCE", "10", null, now.minusSeconds(100)))

        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, now)
        assertThat(snapshot.hasEffectiveHistory).isTrue()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase).isNull()
    }

    @Test
    fun calculateAt_reversal_beforeEffectiveAt_doesNotCancel() = runBlocking {
        val now = Instant.now()
        val t0 = now.minusSeconds(300)
        val t1 = now.minusSeconds(200) // snapshot at t1
        val t2 = now.minusSeconds(100) // reversal at t2

        val original = createMovement("m1", "PURCHASE", "10", "5", t0)
        val reversal = createExactReversal("m2", original, t2)

        db.inventoryMovementDao().insert(original)
        db.inventoryMovementDao().insert(reversal)

        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, t1)
        assertThat(snapshot.hasEffectiveHistory).isTrue()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase?.compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    @Test
    fun calculateAt_reversal_afterEffectiveAt_cancels() = runBlocking {
        val now = Instant.now()
        val t0 = now.minusSeconds(300)
        val t1 = now.minusSeconds(200)
        val t2 = now.minusSeconds(100) // snapshot at t2

        val original = createMovement("m1", "PURCHASE", "10", "5", t0)
        val reversal = createExactReversal("m2", original, t1)

        db.inventoryMovementDao().insert(original)
        db.inventoryMovementDao().insert(reversal)

        val snapshot = service.calculateAt(restaurantId, ingredientId, areaId, t2)
        assertThat(snapshot.hasEffectiveHistory).isFalse()
        assertThat(snapshot.areaQuantityBase.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(snapshot.ingredientAverageCostBase).isNull()
    }

    private fun createExactReversal(
        id: String,
        original: InventoryMovementEntity,
        effectiveAt: Instant,
        createdAt: Instant = effectiveAt
    ): InventoryMovementEntity {
        return InventoryMovementEntity(
            id = id,
            restaurantId = original.restaurantId,
            ingredientId = original.ingredientId,
            areaId = original.areaId,
            movementType = InventoryMovementType.REVERSAL.name,
            quantityBaseSigned =
                BigDecimal(original.quantityBaseSigned)
                    .negate()
                    .toPlainString(),
            unitCostBaseSnapshot =
                original.unitCostBaseSnapshot,
            totalValueSnapshot =
                original.totalValueSnapshot
                    ?.let {
                        BigDecimal(it)
                            .negate()
                            .toPlainString()
                    },
            effectiveAt = effectiveAt.toEpochMilli(),
            sourceDocumentType =
                original.sourceDocumentType,
            sourceDocumentId =
                original.sourceDocumentId,
            sourceOperationId =
                InventoryMovementOperationIds
                    .reversal(original.id),
            sourceLineId =
                original.sourceLineId,
            reversalOfMovementId =
                original.id,
            createdAt =
                createdAt.toEpochMilli()
        )
    }

    private fun createMovement(
        id: String,
        type: String,
        quantityBaseSigned: String,
        unitCostBaseSnapshot: String?,
        effectiveAt: Instant,
        reversalOfMovementId: String? = null,
        sourceDocumentType: String = SourceDocumentType.PURCHASE_RECEIPT.name,
        sourceDocumentId: String = "document-$id",
        sourceLineId: String? = "line-$id",
        createdAt: Instant = effectiveAt,
        sourceOperationIdOverride: String? = null
    ) = InventoryMovementEntity(
        id = id,
        restaurantId = restaurantId.value,
        ingredientId = ingredientId.value,
        areaId = areaId.value,
        movementType = type,
        quantityBaseSigned = quantityBaseSigned,
        unitCostBaseSnapshot = unitCostBaseSnapshot,
        totalValueSnapshot = unitCostBaseSnapshot?.let {
            BigDecimal(quantityBaseSigned).multiply(BigDecimal(it)).toPlainString()
        },
        effectiveAt = effectiveAt.toEpochMilli(),
        sourceDocumentType = sourceDocumentType,
        sourceDocumentId = sourceDocumentId,
        sourceOperationId = sourceOperationIdOverride
            ?: when {
                type == InventoryMovementType.REVERSAL.name &&
                        reversalOfMovementId != null ->
                    InventoryMovementOperationIds.reversal(reversalOfMovementId)

                type == InventoryMovementType.PURCHASE.name &&
                        sourceLineId != null ->
                    InventoryMovementOperationIds.purchasePost(
                        sourceDocumentId,
                        sourceLineId
                    )

                type == InventoryMovementType.WASTE.name ->
                    InventoryMovementOperationIds.wastePost(sourceDocumentId)

                else ->
                    "test-operation:$id"
            },
        sourceLineId = sourceLineId,
        reversalOfMovementId = reversalOfMovementId,
        createdAt = createdAt.toEpochMilli()
    )
}
