package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class BackupIsolationTest {

    private lateinit var db: RestaurantInventoryDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createSnapshot_isolatesAllTablesByRestaurant() = runBlocking {
        // Global Reference Data
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))

        // Restaurant 1 setup
        db.restaurantDao().insert(RestaurantEntity("rest-1", "Rest 1", "USD", "en", 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", "rest-1", "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.ingredientCategoryDao().upsert(IngredientCategoryEntity("cat-1", "rest-1", "Cat 1", "cat-1", 1, true, 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing-1", "rest-1", "Ing 1", "ing 1", "cat-1", "u1", "area-1", null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
        db.supplierDao().insert(SupplierEntity("sup-1", "rest-1", "Sup 1", "sup 1", null, null, null, true, 0L, 0L, null))
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p-1", "rest-1", "sup-1", null, 0L, DocumentStatus.POSTED.name, null, null, null, 0L, 0L, 0L, null))
        db.purchaseDao().insertLine(PurchaseLineEntity("pl-1", "p-1", "ing-1", "area-1", "opt-1", "1", "1", "10.0", "10.0", null, 0L, 0L))
        
        db.stockCountDao().insertCount(StockCountEntity("sc-1", "rest-1", "sc-1-name", 0L, 0L, null, DocumentStatus.DRAFT.name, null, 0L, 0L, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("sca-1", "sc-1", "area-1", DocumentStatus.DRAFT.name, null, null, 1)))
        // Corrected StockCountLineEntity constructor call
        db.stockCountDao().insertCountLine(StockCountLineEntity("scl-1", "sca-1", "ing-1", "opt-1", "1", "1", null, null, null, 0L, 0L))
        
        db.wasteDao().insert(WasteEventEntity("w-1", "rest-1", "ing-1", "area-1", "opt-1", "1", "1", "SPOILED", 0L, null, null, null, DocumentStatus.POSTED.name, 0L, 0L, 0L, null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity("rest-1", "ing-1", "area-1", "10.0", 0L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity("rest-1", "ing-1", "10.0", 0L))
        db.inventoryMovementDao().insert(InventoryMovementEntity("im-1", "rest-1", "ing-1", "area-1", "PURCHASE", "10", "1", "10", 0L, "PURCHASE_RECEIPT", "p-1", "op-1", "pl-1", null, 0L))

        // Restaurant 2 setup
        db.restaurantDao().insert(RestaurantEntity("rest-2", "Rest 2", "USD", "en", 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-2", "rest-2", "Area 2", "area 2", 1, true, 0L, 0L, null))
        db.ingredientCategoryDao().upsert(IngredientCategoryEntity("cat-2", "rest-2", "Cat 2", "cat 2", 1, true, 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing-2", "rest-2", "Ing 2", "ing 2", "cat-2", "u1", "area-2", null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-2", "ing-2", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
        db.supplierDao().insert(SupplierEntity("sup-2", "rest-2", "Sup 2", "sup 2", null, null, null, true, 0L, 0L, null))
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p-2", "rest-2", "sup-2", null, 0L, DocumentStatus.POSTED.name, null, null, null, 0L, 0L, 0L, null))
        
        db.stockCountDao().insertCount(StockCountEntity("sc-2", "rest-2", "sc-2-name", 0L, 0L, null, DocumentStatus.DRAFT.name, null, 0L, 0L, null))
        
        db.wasteDao().insert(WasteEventEntity("w-2", "rest-2", "ing-2", "area-2", "opt-2", "1", "1", "SPOILED", 0L, null, null, null, DocumentStatus.POSTED.name, 0L, 0L, 0L, null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity("rest-2", "ing-2", "area-2", "20.0", 0L))
        db.inventoryMovementDao().insert(InventoryMovementEntity("im-2", "rest-2", "ing-2", "area-2", "PURCHASE", "20", "1", "20", 0L, "PURCHASE_RECEIPT", "p-2", "op-2", null, null, 0L))

        // Snapshot for Restaurant 1
        val snapshot = db.backupDao().createSnapshot("rest-1")
        
        assertThat(snapshot.restaurants).hasSize(1)
        assertThat(snapshot.restaurants[0].id).isEqualTo("rest-1")
        assertThat(snapshot.inventoryAreas).hasSize(1)
        assertThat(snapshot.inventoryAreas[0].id).isEqualTo("area-1")
        assertThat(snapshot.ingredientCategories).hasSize(1)
        assertThat(snapshot.ingredientCategories[0].id).isEqualTo("cat-1")
        assertThat(snapshot.ingredients).hasSize(1)
        assertThat(snapshot.ingredients[0].id).isEqualTo("ing-1")
        assertThat(snapshot.ingredientUnitOptions).hasSize(1)
        assertThat(snapshot.ingredientUnitOptions[0].id).isEqualTo("opt-1")
        assertThat(snapshot.suppliers).hasSize(1)
        assertThat(snapshot.suppliers[0].id).isEqualTo("sup-1")
        assertThat(snapshot.purchaseReceipts).hasSize(1)
        assertThat(snapshot.purchaseReceipts[0].id).isEqualTo("p-1")
        assertThat(snapshot.purchaseLines).hasSize(1)
        assertThat(snapshot.purchaseLines[0].id).isEqualTo("pl-1")
        assertThat(snapshot.stockCounts).hasSize(1)
        assertThat(snapshot.stockCounts[0].id).isEqualTo("sc-1")
        assertThat(snapshot.stockCountAreas).hasSize(1)
        assertThat(snapshot.stockCountAreas[0].id).isEqualTo("sca-1")
        assertThat(snapshot.stockCountLines).hasSize(1)
        assertThat(snapshot.stockCountLines[0].id).isEqualTo("scl-1")
        assertThat(snapshot.wasteEvents).hasSize(1)
        assertThat(snapshot.wasteEvents[0].id).isEqualTo("w-1")
        assertThat(snapshot.inventoryMovements).hasSize(1)
        assertThat(snapshot.inventoryMovements[0].id).isEqualTo("im-1")
        assertThat(snapshot.inventoryBalanceProjections).hasSize(1)
        assertThat(snapshot.inventoryBalanceProjections[0].restaurantId).isEqualTo("rest-1")
        assertThat(snapshot.ingredientCostProjections).hasSize(1)
        assertThat(snapshot.ingredientCostProjections[0].restaurantId).isEqualTo("rest-1")
        
        // Global reference data (Units) MUST be included
        assertThat(snapshot.units).isNotEmpty()
    }
}
