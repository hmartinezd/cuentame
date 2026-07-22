package com.miara.cuentame.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.repository.RoomInventoryProjectionRebuilder
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ProjectionRebuildTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var rebuilder: RoomInventoryProjectionRebuilder
    private val fixedTime = Instant.parse("2024-01-01T00:00:00Z")
    private val timeProvider = object : TimeProvider {
        override fun now() = fixedTime
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rebuilder = RoomInventoryProjectionRebuilder(
            db,
            db.ingredientDao(),
            db.inventoryMovementDao(),
            db.inventoryProjectionDao(),
            db.ingredientCostProjectionDao(),
            WeightedAverageCostCalculator(),
            timeProvider
        )
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            db.inventoryAreaDao().upsert(TestFactories.createArea())
            db.ingredientDao().upsert(TestFactories.createIngredient())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun rebuildBasicWeightedAverage() = runBlocking {
        insertMovement("mov_1", "10", "2", InventoryMovementType.PURCHASE)
        insertMovement("mov_2", "10", "4", InventoryMovementType.PURCHASE)

        rebuilder.rebuildForIngredient(IngredientId("ing_1"))

        val cost = db.ingredientCostProjectionDao().observeCostForIngredient("ing_1").first()
        assertThat(BigDecimal(cost?.averageUnitCostBase).compareTo(BigDecimal("3"))).isEqualTo(0)
    }

    @Test
    fun rebuildWithReversal() = runBlocking {
        insertMovement("mov_1", "10", "2", InventoryMovementType.PURCHASE)
        insertMovement("mov_2", "10", "4", InventoryMovementType.PURCHASE)
        // Reverse first purchase
        insertReversal("rev_1", "mov_1", "-10", "2")

        rebuilder.rebuildForIngredient(IngredientId("ing_1"))

        val cost = db.ingredientCostProjectionDao().observeCostForIngredient("ing_1").first()
        assertThat(BigDecimal(cost?.averageUnitCostBase).compareTo(BigDecimal("4"))).isEqualTo(0)
        
        val balance = db.inventoryProjectionDao().getBalance("ing_1", "area_1")
        assertThat(BigDecimal(balance?.quantityBase).compareTo(BigDecimal("10"))).isEqualTo(0)
    }

    private suspend fun insertMovement(id: String, qty: String, cost: String?, type: InventoryMovementType) {
        db.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id, "rest_1", "ing_1", "area_1",
                type.name, qty, cost, null,
                0, SourceDocumentType.PURCHASE_RECEIPT.name, "doc_1", id, null, null, 0
            )
        )
    }

    private suspend fun insertReversal(id: String, originalId: String, qty: String, cost: String?) {
        db.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id, "rest_1", "ing_1", "area_1",
                InventoryMovementType.REVERSAL.name, qty, cost, null,
                0, SourceDocumentType.PURCHASE_RECEIPT.name, "doc_1", id, null, originalId, 0
            )
        )
    }
}
