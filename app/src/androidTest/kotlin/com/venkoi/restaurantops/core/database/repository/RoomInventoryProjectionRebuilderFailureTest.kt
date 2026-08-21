package com.venkoi.restaurantops.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.IngredientCostProjectionEntity
import com.venkoi.restaurantops.core.database.entity.InventoryBalanceProjectionEntity
import com.venkoi.restaurantops.core.database.entity.InventoryMovementEntity
import com.venkoi.restaurantops.core.domain.service.HistoricalInventoryCostCalculator
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementOperationIds
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomInventoryProjectionRebuilderFailureTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var rebuilder: RoomInventoryProjectionRebuilder
    private val restaurantId = RestaurantId("rest_1")
    private val ingredientId = IngredientId("ing_1")
    private val areaId = "area_1"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val timeProvider = mockk<TimeProvider>()
        every { timeProvider.now() } returns Instant.now()

        val inventoryValidator = InventoryMovementValidator()
        val historyValidator = InventoryMovementHistoryValidator(inventoryValidator)

        rebuilder = RoomInventoryProjectionRebuilder(
            db,
            db.ingredientDao(),
            db.inventoryMovementDao(),
            db.inventoryProjectionDao(),
            db.ingredientCostProjectionDao(),
            HistoricalInventoryCostCalculator(),
            historyValidator,
            timeProvider
        )

        runBlocking {
            db.restaurantDao().insert(com.venkoi.restaurantops.core.database.entity.RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null))
            db.unitDao().insertSeedUnits(listOf(com.venkoi.restaurantops.core.database.entity.UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)))
            db.inventoryAreaDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity(areaId, restaurantId.value, "Area 1", "area 1", 0, true, 0, 0, null))
            db.ingredientDao().insert(com.venkoi.restaurantops.core.database.entity.IngredientEntity(ingredientId.value, restaurantId.value, "Ing 1", "ing 1", null, "mass_lb", null, null, null, null, true, 0, 0, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedValidProjections() {
        db.inventoryProjectionDao().upsert(
            InventoryBalanceProjectionEntity(restaurantId.value, ingredientId.value, areaId, "100", 1000L)
        )
        db.ingredientCostProjectionDao().upsert(
            IngredientCostProjectionEntity(restaurantId.value, ingredientId.value, "10.00", 1000L)
        )
    }

    private suspend fun verifyProjectionsUnchanged() {
        val balance = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId)
        assertThat(balance?.quantityBase).isEqualTo("100")
        val cost = db.ingredientCostProjectionDao().getCost(ingredientId.value)
        assertThat(cost?.averageUnitCostBase).isEqualTo("10.00")
    }

    @Test
    fun insertReversal_withMissingTarget_isRejectedByDatabase() = runBlocking {
        seedValidProjections()
        // reversalOfMovementId has a foreign key to inventory_movements.id
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOfMovementId = "missing"))
            }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun insertSecondReversal_forSameOriginal_isRejectedByDatabase() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now.minusSeconds(50), reversalOfMovementId = "m1"))
        
        // reversalOfMovementId has a UNIQUE index
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.inventoryMovementDao().insert(createMovement("m3", "REVERSAL", "-10", "5", now, reversalOfMovementId = "m1"))
            }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalQuantity_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(quantityBaseSigned = "-9")
        
        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(malformed)).isNotNull()
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalCost_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(unitCostBaseSnapshot = "6")

        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(malformed)).isNotNull()
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalArea_rollsBack() = runBlocking {
        seedValidProjections()
        // Area 2 exists in same restaurant
        db.inventoryAreaDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity("area_2", restaurantId.value, "Area 2", "area_2", 1, true, 0, 0, null))
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(areaId = "area_2")
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalOperationId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now.minusSeconds(100))
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(sourceOperationId = "wrong")
        
        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(malformed)).isNotNull()

        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_malformedDecimal_rollsBack() = runBlocking {
        seedValidProjections()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "invalid", "5", Instant.now()))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_before_original_effectiveAt_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", InventoryMovementType.PURCHASE.name, "10", "5", now)
        val validReversal = createExactReversal("m2", original, now.minusSeconds(1))

        assertThat(db.inventoryMovementDao().insert(original)).isNotNull()
        assertThat(db.inventoryMovementDao().insert(validReversal)).isNotNull()
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_before_original_createdAt_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now)
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(createdAt = now.minusSeconds(1).toEpochMilli())
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceDocumentId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now)
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(sourceDocumentId = "wrong")
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_withNullTarget_rollsBack() = runBlocking {
        seedValidProjections()
        // Type REVERSAL but reversalOfMovementId is null.
        // This is database-representable because reversalOfMovementId is nullable.
        db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOfMovementId = "m0").copy(reversalOfMovementId = null))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversalTargetingAnotherReversal_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val firstReversal = createExactReversal("m2", original, now.minusSeconds(50))
        db.inventoryMovementDao().insert(firstReversal)
        
        // m3 targets m2 (which is a REVERSAL). Must inherit m2 identities.
        val invalidTargetReversal = createExactReversal("m3", firstReversal, now)
        db.inventoryMovementDao().insert(invalidTargetReversal)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_withWrongTotalValue_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(totalValueSnapshot = "-49")
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceDocumentType_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(sourceDocumentType = "WASTE_EVENT")
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceLineId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        db.inventoryMovementDao().insert(original)
        
        val validReversal = createExactReversal("m2", original, now)
        val malformed = validReversal.copy(sourceLineId = "wrong")
        db.inventoryMovementDao().insert(malformed)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_blankSourceOperationId_rollsBack() = runBlocking {
        seedValidProjections()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", Instant.now()).copy(sourceOperationId = " "))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_invalidSourceDocumentType_rollsBack() = runBlocking {
        seedValidProjections()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", Instant.now()).copy(sourceDocumentType = "INVALID"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_positiveReversal_clearsProjections() = runBlocking {
        val now = Instant.now()
        val original = createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100))
        val reversal = createExactReversal("m2", original, now)
        
        db.inventoryMovementDao().insert(original)
        db.inventoryMovementDao().insert(reversal)
        
        rebuilder.rebuildForIngredient(ingredientId)
        
        val balance = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId)
        assertThat(balance).isNull()
        val cost = db.ingredientCostProjectionDao().getCost(ingredientId.value)
        assertThat(cost).isNull()
    }

    @Test
    fun rebuild_partialReversal_preservesRemainingHistory() = runBlocking {
        val now = Instant.now()
        val m1 = createMovement(
            id = "m1",
            type = InventoryMovementType.PURCHASE.name,
            quantityBaseSigned = "10",
            unitCostBaseSnapshot = "5",
            effectiveAt = now.minusSeconds(200),
            sourceDocumentId = "purchase-1",
            sourceLineId = "line-1"
        )
        db.inventoryMovementDao().insert(m1)
        
        val m2 = createMovement(
            id = "m2",
            type = InventoryMovementType.PURCHASE.name,
            quantityBaseSigned = "10",
            unitCostBaseSnapshot = "15",
            effectiveAt = now.minusSeconds(100),
            sourceDocumentId = "purchase-2",
            sourceLineId = "line-2"
        )
        db.inventoryMovementDao().insert(m2)
        
        val m3 = createExactReversal(
            id = "m3",
            original = m2,
            effectiveAt = now
        )
        db.inventoryMovementDao().insert(m3)

        rebuilder.rebuildForIngredient(ingredientId)

        val balance = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId)
        assertThat(balance?.quantityBase).isEqualTo("10")
        val cost = db.ingredientCostProjectionDao().getCost(ingredientId.value)
        assertThat(BigDecimal(cost?.averageUnitCostBase!!).compareTo(BigDecimal("5"))).isEqualTo(0)
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
        areaId = areaId,
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
