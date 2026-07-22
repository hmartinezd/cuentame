package com.miara.cuentame.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReversalTest {
    private lateinit var db: RestaurantInventoryDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
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
    fun onlyOneReversalAllowedPerMovement() = runBlocking {
        val original = createMovement("mov_1", "10")
        db.inventoryMovementDao().insert(original)

        val reversal1 = createMovement("rev_1", "-10", original.id)
        db.inventoryMovementDao().insert(reversal1)

        val reversal2 = createMovement("rev_2", "-10", original.id)
        try {
            db.inventoryMovementDao().insert(reversal2)
            assertThat(true).isFalse() // Should not reach here
        } catch (e: Exception) {
            assertThat(e).isNotNull()
        }
    }

    private fun createMovement(id: String, qty: String, reversalOf: String? = null) = InventoryMovementEntity(
        id, "rest_1", "ing_1", "area_1",
        if (reversalOf == null) InventoryMovementType.PURCHASE.name else InventoryMovementType.REVERSAL.name,
        qty, null, null,
        0, SourceDocumentType.PURCHASE_RECEIPT.name, "doc_1", id, null, reversalOf, 0
    )
}
