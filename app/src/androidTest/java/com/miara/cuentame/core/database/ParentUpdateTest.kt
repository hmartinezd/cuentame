package com.miara.cuentame.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.inventory.DocumentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ParentUpdateTest {
    private lateinit var db: RestaurantInventoryDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            db.inventoryAreaDao().upsert(TestFactories.createArea())
            db.ingredientDao().upsert(TestFactories.createIngredient())
            db.ingredientUnitOptionDao().upsert(
                IngredientUnitOptionEntity(
                    "opt_1", "ing_1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0, 0, null
                )
            )
        }
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun updateReceiptPreservesLines() = runBlocking {
        val receiptId = "receipt_1"
        val receipt = PurchaseReceiptEntity(
            receiptId, "rest_1", null, null, Instant.now().toEpochMilli(),
            DocumentStatus.DRAFT.name, null, null, 0, 0, null, null
        )
        db.purchaseDao().insertReceipt(receipt)

        val line = PurchaseLineEntity(
            "line_1", receiptId, "ing_1", "area_1", "opt_1",
            "10", "10", "100", "10", null, 0, 0
        )
        // Correcting to insertLine as upsertLines was removed/not available in current DAO
        db.purchaseDao().insertLine(line)

        // Update receipt
        val updatedReceipt = receipt.copy(notes = "Updated notes")
        db.purchaseDao().updateReceipt(updatedReceipt)

        // Verify line still exists
        val lines = db.purchaseDao().getLinesForReceipt(receiptId)
        assertThat(lines).hasSize(1)
        assertThat(lines[0].id).isEqualTo("line_1")
    }
}
