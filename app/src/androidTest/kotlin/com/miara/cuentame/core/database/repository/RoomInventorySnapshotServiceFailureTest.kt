package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
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
        service = RoomInventorySnapshotService(
            db.inventoryMovementDao(),
            WeightedAverageCostCalculator(),
            InventoryMovementValidator()
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
    fun calculateAt_reversalWithoutTarget_throws() {
        runBlocking {
            db.inventoryMovementDao().insert(createMovement("m1", "REVERSAL", "-10", "5", Instant.now(), reversalOf = null))
            assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
                runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
            }
        }
    }

    @Test
    fun calculateAt_reversalOfReversal_throws() {
        runBlocking {
            val now = Instant.now()
            db.inventoryMovementDao().insert(createMovement("m1", "PURCHASE", "10", "5", now.minusSeconds(100)))
            db.inventoryMovementDao().insert(createMovement("m2", "REVERSAL", "-10", "5", now.minusSeconds(50), reversalOf = "m1"))
            db.inventoryMovementDao().insert(createMovement("m3", "REVERSAL", "10", "5", now, reversalOf = "m2"))
            
            assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
                runBlocking { service.calculateAt(restaurantId, ingredientId, areaId, Instant.now()) }
            }
        }
    }

    private fun createMovement(
        id: String,
        type: String,
        qty: String,
        cost: String?,
        effectiveAt: Instant,
        reversalOf: String? = null
    ) = InventoryMovementEntity(
        id = id,
        restaurantId = restaurantId.value,
        ingredientId = ingredientId.value,
        areaId = areaId.value,
        movementType = type,
        quantityBaseSigned = qty,
        unitCostBaseSnapshot = cost,
        totalValueSnapshot = cost?.let { BigDecimal(qty).multiply(BigDecimal(it)).toPlainString() },
        effectiveAt = effectiveAt.toEpochMilli(),
        sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
        sourceDocumentId = "doc_1",
        sourceOperationId = "op_$id",
        sourceLineId = "line_1",
        reversalOfMovementId = reversalOf,
        createdAt = effectiveAt.toEpochMilli()
    )
}
