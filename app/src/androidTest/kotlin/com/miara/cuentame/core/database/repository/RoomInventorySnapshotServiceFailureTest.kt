package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.domain.validation.ValidationError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomInventorySnapshotServiceFailureTest {
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
            db.restaurantDao().insert(com.miara.cuentame.core.database.entity.RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null))
            db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)))
            db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restaurantId.value, "Area 1", "area 1", 0, true, 0, 0, null))
            db.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingredientId.value, restaurantId.value, "Ing 1", "ing 1", null, "mass_lb", null, null, null, null, true, 0, 0, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun calculateAt_reversalWithoutTarget_throws() = runBlocking {
        // Manual construction to bypass createMovement's automatic opId fix
        db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOfMovementId = "m0").copy(reversalOfMovementId = null))
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_reversal_identityMismatch_throws() = runBlocking {
        val now = Instant.now()
        // Area 2 exists in same restaurant
        db.inventoryAreaDao().upsert(
            com.miara.cuentame.core.database.entity.InventoryAreaEntity(
                "area_2",
                restaurantId.value,
                "Area 2",
                "area_2",
                1,
                true,
                0,
                0,
                null
            )
        )

        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)

        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(areaId = "area_2")

        db.inventoryMovementDao().insert(malformed)

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_reversal_operationIdMismatch_throws() = runBlocking {
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(sourceOperationId = "wrong_op")
        
        db.inventoryMovementDao().insert(malformed)

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_reversal_targetingAnotherReversal_throws() = runBlocking {
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(
            createMovement(
                id = "m2",
                type = InventoryMovementType.REVERSAL.name,
                quantityBaseSigned = "-10",
                unitCostBaseSnapshot = "5",
                effectiveAt = now.minusSeconds(50),
                reversalOfMovementId = "m1"
            )
        )
        // m3 targets m2 (which is a REVERSAL)
        db.inventoryMovementDao().insert(
            createMovement(
                id = "m3",
                type = InventoryMovementType.REVERSAL.name,
                quantityBaseSigned = "10",
                unitCostBaseSnapshot = "5",
                effectiveAt = now,
                reversalOfMovementId = "m2"
            )
        )

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_duplicateReversalTarget_isRejectedByDatabase() = runBlocking {
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(
            createMovement(
                id = "m2",
                type = InventoryMovementType.REVERSAL.name,
                quantityBaseSigned = "-10",
                unitCostBaseSnapshot = "5",
                effectiveAt = now.minusSeconds(50),
                reversalOfMovementId = "m1"
            )
        )

        // UNIQUE index on reversalOfMovementId
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.inventoryMovementDao().insert(
                    createMovement(
                        id = "m3",
                        type = InventoryMovementType.REVERSAL.name,
                        quantityBaseSigned = "-10",
                        unitCostBaseSnapshot = "5",
                        effectiveAt = now,
                        reversalOfMovementId = "m1"
                    )
                )
            }
        }
    }

    @Test
    fun calculateAt_wrongReversalQuantity_throws() = runBlocking {
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(quantityBaseSigned = "-9")

        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(malformed)).isNotNull()

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_wrongReversalCost_throws() = runBlocking {
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(unitCostBaseSnapshot = "6")

        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(malformed)).isNotNull()

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_malformedDecimal_throws() = runBlocking {
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "invalid", "5", Instant.now()))
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAt_invalidMovementType_throws() = runBlocking {
        db.inventoryMovementDao().insert(createMovement("m1", "INVALID", "10", "5", Instant.now()))
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
        }
    }

    @Test
    fun calculateAreaBalancesAt_malformedHistory_throws() = runBlocking {
        val now = Instant.now()
        // Ing 1 is valid
        db.inventoryMovementDao().insert(
            createMovement(
                id = "m1",
                type = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10",
                unitCostBaseSnapshot = "5",
                effectiveAt = now.minusSeconds(100),
                sourceDocumentId = "purchase-1",
                sourceLineId = "line-1"
            )
        )

        // Ing 2 has valid physical graph but semantic mismatch (wrong operation ID)
        db.ingredientDao().insert(
            com.miara.cuentame.core.database.entity.IngredientEntity(
                "ing_2",
                restaurantId.value,
                "Ing 2",
                "ing 2",
                null,
                "mass_lb",
                null,
                null,
                null,
                null,
                true,
                0,
                0,
                null
            )
        )
        val original2 = createMovement(
            id = "m2",
            type = InventoryMovementType.PURCHASE.name,
            quantityBaseSigned = "10",
            unitCostBaseSnapshot = "5",
            effectiveAt = now.minusSeconds(100),
            sourceDocumentId = "purchase-2",
            sourceLineId = "line-2"
        ).copy(ingredientId = "ing_2")
        db.inventoryMovementDao().insert(original2)
        
        val validReversal2 = createExactReversal("m3", original2, now)
        val malformed2 = validReversal2.copy(sourceOperationId = "wrong-operation")
        
        db.inventoryMovementDao().insert(malformed2)

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { service.calculateAreaBalancesAt(restaurantId, areaId, Instant.now()) }
        }
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
            try {
                BigDecimal(quantityBaseSigned).multiply(BigDecimal(it)).toPlainString()
            } catch (e: Exception) {
                null
            }
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
