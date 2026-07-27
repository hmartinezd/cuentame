package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ReportingIsolationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomDetailedReportsRepository
    private val rest1 = RestaurantId("rest-1")
    private val rest2 = RestaurantId("rest-2")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        repository = RoomDetailedReportsRepository(
            db.inventoryProjectionDao(),
            db.purchaseDao(),
            db.inventoryMovementDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seedBasicUnits() {
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
    }

    @Test
    fun inventoryDetails_hardenedIsolation_excludesCrossRestaurantMetadata() = runBlocking {
        seedBasicUnits()
        db.restaurantDao().insert(RestaurantEntity(rest1.value, "Rest 1", "USD", "en", 0L, 0L, null))
        db.restaurantDao().insert(RestaurantEntity(rest2.value, "Rest 2", "USD", "en", 0L, 0L, null))
        
        // Ingredient belongs to Rest 2
        db.ingredientDao().insert(IngredientEntity("ing-shared", rest2.value, "SECRET", "secret", null, "u1", null, null, null, null, true, 0L, 0L, null))

        // CORRUPTION: Rest 1 has a balance projection referencing an ingredient ID that belongs to Rest 2
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(rest1.value, "ing-shared", "area1", "10.0", 1000L))

        val report = repository.observeInventoryDetails(rest1).first()
        
        // Hardened JOIN i.restaurantId = ibp.restaurantId must fail
        assertThat(report.rows).isEmpty()
    }

    @Test
    fun purchaseDetails_hardenedIsolation_excludesCrossRestaurantSuppliers() = runBlocking {
        seedBasicUnits()
        db.restaurantDao().insert(RestaurantEntity(rest1.value, "Rest 1", "USD", "en", 0L, 0L, null))
        db.restaurantDao().insert(RestaurantEntity(rest2.value, "Rest 2", "USD", "en", 0L, 0L, null))

        // Valid metadata for Rest 1
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", rest1.value, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing1", rest1.value, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

        // Supplier belongs to Rest 2
        db.supplierDao().insert(SupplierEntity("s1", rest2.value, "SECRET", "secret", null, null, null, true, 0L, 0L, null))

        // Purchase belongs to Rest 1 but references Supplier 1 (malformed cross-restaurant link)
        val receipt = PurchaseReceiptEntity("p1", rest1.value, "s1", null, 1000L, DocumentStatus.POSTED.name, null, null, 0L, 0L, 1000L, null)
        db.purchaseDao().insertReceipt(receipt)
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing1", "area1", "opt1", "1", "1", "100.0", "1", null, 0L, 0L))

        val period = com.miara.cuentame.core.domain.service.ReportingPeriod(java.time.Instant.ofEpochMilli(500), java.time.Instant.ofEpochMilli(1500))
        val report = repository.observePurchaseDetails(rest1, period).first()
        
        assertThat(report.rows).hasSize(1)
        // JOIN s.restaurantId = pr.restaurantId must fail -> supplierName remains null
        assertThat(report.rows[0].supplierName).isNull()
    }

    @Test
    fun wasteDetails_hardenedIsolation_excludesCrossRestaurantMetadata() = runBlocking {
        seedBasicUnits()
        db.restaurantDao().insert(RestaurantEntity(rest1.value, "Rest 1", "USD", "en", 0L, 0L, null))
        db.restaurantDao().insert(RestaurantEntity(rest2.value, "Rest 2", "USD", "en", 0L, 0L, null))

        // Ingredient and Area belong to Rest 2
        db.ingredientDao().insert(IngredientEntity("ing1", rest2.value, "SECRET", "secret", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", rest2.value, "SECRET", "secret", 1, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

        // Waste event and Movement belong to Rest 1 (CORRUPTION: reference Rest 2 metadata)
        db.wasteDao().insert(WasteEventEntity("w1", rest1.value, "ing1", "area1", "opt1", "10", "1", "SPOILED", 1000L, null, null, DocumentStatus.POSTED.name, 0L, 0L, 1000L, null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m1", rest1.value, "ing1", "area1", InventoryMovementType.WASTE.name, "-10", "1", "50.0", 1000L, SourceDocumentType.WASTE_EVENT.name, "w1", "op1", "m1", null, 0L))

        val period = com.miara.cuentame.core.domain.service.ReportingPeriod(java.time.Instant.ofEpochMilli(500), java.time.Instant.ofEpochMilli(1500))
        val report = repository.observeWasteDetails(rest1, period).first()
        
        // Hardened JOINs on i.restaurantId, ia.restaurantId, we.restaurantId must match im.restaurantId
        assertThat(report.rows).isEmpty()
    }
}
