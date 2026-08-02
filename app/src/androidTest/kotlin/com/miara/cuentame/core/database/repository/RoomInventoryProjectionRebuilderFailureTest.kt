package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.domain.validation.ValidationError
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
            db.restaurantDao().insert(com.miara.cuentame.core.database.entity.RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null))
            db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)))
            db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId, restaurantId.value, "Area 1", "area 1", 0, true, 0, 0, null))
            db.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingredientId.value, restaurantId.value, "Ing 1", "ing 1", null, "mass_lb", null, null, null, null, true, 0, 0, null))
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
                db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOf = "missing"))
            }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun insertSecondReversal_forSameOriginal_isRejectedByDatabase() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now.minusSeconds(50), reversalOf = "m1"))
        
        // reversalOfMovementId has a UNIQUE index
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.inventoryMovementDao().insert(createMovement("m3", "REVERSAL", "-10", "5", now, reversalOf = "m1"))
            }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalQuantity_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-9", "5", now, reversalOf = "m1"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalCost_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "6", now, reversalOf = "m1"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalArea_rollsBack() = runBlocking {
        seedValidProjections()
        // Area 2 exists in same restaurant
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity("area_2", restaurantId.value, "Area 2", "area_2", 1, true, 0, 0, null))
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(areaId = "area_2"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongReversalOperationId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(sourceOperationId = "wrong"))
        
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
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now.minusSeconds(1), reversalOf = "m1"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_before_original_createdAt_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(createdAt = now.minusSeconds(1).toEpochMilli()))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceDocumentId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(sourceDocumentId = "wrong"))
        
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
        db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOf = "m0").copy(reversalOfMovementId = null))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversalTargetingAnotherReversal_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now.minusSeconds(50), reversalOf = "m1"))
        // m3 targets m2 (which is a REVERSAL)
        db.inventoryMovementDao().insert(createMovement("m3", "REVERSAL", "10", "5", now, reversalOf = "m2"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_reversal_withWrongTotalValue_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        // Correct is -50
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(totalValueSnapshot = "-49"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceDocumentType_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(sourceDocumentType = "WASTE_EVENT"))
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            runBlocking { rebuilder.rebuildForIngredient(ingredientId) }
        }
        verifyProjectionsUnchanged()
    }

    @Test
    fun rebuild_wrongSourceLineId_rollsBack() = runBlocking {
        seedValidProjections()
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1").copy(sourceLineId = "wrong"))
        
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
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now, reversalOf = "m1"))
        
        rebuilder.rebuildForIngredient(ingredientId)
        
        val balance = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId)
        assertThat(balance).isNull()
        val cost = db.ingredientCostProjectionDao().getCost(ingredientId.value)
        assertThat(cost?.averageUnitCostBase).isNull()
    }

    @Test
    fun rebuild_partialReversal_preservesRemainingHistory() = runBlocking {
        val now = Instant.now()
        db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(200)))
        db.inventoryMovementDao().insert(createMovement("m2", "PURCHASE", "10", "15", now.minusSeconds(100)))
        db.inventoryMovementDao().insert(createMovement("m3", "REVERSAL", "-10", "15", now, reversalOf = "m2"))
        
        rebuilder.rebuildForIngredient(ingredientId)
        
        val balance = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId)
        assertThat(balance?.quantityBase).isEqualTo("10") // Exact string equality depends on DB format
        val cost = db.ingredientCostProjectionDao().getCost(ingredientId.value)
        assertThat(BigDecimal(cost?.averageUnitCostBase!!).compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    private fun createMovement(
        id: String,
        type: String,
        qty: String,
        cost: String?,
        effectiveAt: Instant,
        reversalOf: String? = null
    ): InventoryMovementEntity {
        val opId = when {
            type == "REVERSAL" && reversalOf != null -> InventoryMovementOperationIds.reversal(reversalOf)
            type == "PURCHASE" -> InventoryMovementOperationIds.purchasePost("doc_1", "line_1")
            else -> "op_$id"
        }
        return InventoryMovementEntity(
            id = id,
            restaurantId = restaurantId.value,
            ingredientId = ingredientId.value,
            areaId = areaId,
            movementType = type,
            quantityBaseSigned = qty,
            unitCostBaseSnapshot = cost,
            totalValueSnapshot = cost?.let { 
                try { BigDecimal(qty).multiply(BigDecimal(it)).toPlainString() } catch(e: Exception) { null }
            },
            effectiveAt = effectiveAt.toEpochMilli(),
            sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
            sourceDocumentId = "doc_1",
            sourceOperationId = opId,
            sourceLineId = "line_1",
            reversalOfMovementId = reversalOf,
            createdAt = effectiveAt.toEpochMilli()
        )
    }
}
