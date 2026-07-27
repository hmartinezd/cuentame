package com.miara.cuentame.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class DetailedReportsDaoTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var inventoryProjectionDao: InventoryProjectionDao
    private lateinit var purchaseDao: PurchaseDao
    private lateinit var movementDao: InventoryMovementDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        inventoryProjectionDao = db.inventoryProjectionDao()
        purchaseDao = db.purchaseDao()
        movementDao = db.inventoryMovementDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedDependencies(restId: String) {
        db.restaurantDao().insert(RestaurantEntity(restId, "Rest", "USD", "en", 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", restId, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area2", restId, "Area 2", "area 2", 2, true, 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("ing1", restId, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun inventoryValuationRows_joinsMetadata() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)
            
            inventoryProjectionDao.upsert(InventoryBalanceProjectionEntity(restId, "ing1", "area1", "10.0", 1000L))
            
            val rows = inventoryProjectionDao.observeValuationRows(restId).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].ingredientName).isEqualTo("Ing 1")
            assertThat(rows[0].baseUnitSymbol).isEqualTo("u")
            assertThat(rows[0].areaId).isEqualTo("area1")
        }
    }

    @Test
    fun inventoryValuationRows_includesNegativeBalances() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)
            
            inventoryProjectionDao.upsert(InventoryBalanceProjectionEntity(restId, "ing1", "area1", "-5.0", 1000L))
            
            val rows = inventoryProjectionDao.observeValuationRows(restId).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].quantityBase).isEqualTo("-5.0")
        }
    }

    @Test
    fun purchaseSpendRows_filtersByStatus() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)
            
            // POSTED
            purchaseDao.insertReceipt(createReceipt("p1", restId, 1000L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l1", "p1", "100.0"))
            
            // DRAFT
            purchaseDao.insertReceipt(createReceipt("p2", restId, 1000L, DocumentStatus.DRAFT.name))
            purchaseDao.insertLine(createLine("l2", "p2", "50.0"))
            
            // VOIDED
            purchaseDao.insertReceipt(createReceipt("p3", restId, 1000L, DocumentStatus.VOIDED.name))
            purchaseDao.insertLine(createLine("l3", "p3", "75.0"))

            val rows = purchaseDao.observeSpendRows(restId, 500L, 1500L).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].receiptId).isEqualTo("p1")
        }
    }

    @Test
    fun purchaseSpendRows_filtersByDateRange() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)
            
            // On boundary (inclusive)
            purchaseDao.insertReceipt(createReceipt("p1", restId, 1000L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l1", "p1", "10.0"))
            
            // Inside
            purchaseDao.insertReceipt(createReceipt("p2", restId, 1100L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l2", "p2", "20.0"))
            
            // On boundary (exclusive)
            purchaseDao.insertReceipt(createReceipt("p3", restId, 1500L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l3", "p3", "30.0"))
            
            // Outside
            purchaseDao.insertReceipt(createReceipt("p4", restId, 500L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l4", "p4", "40.0"))

            val rows = purchaseDao.observeSpendRows(restId, 1000L, 1500L).first()
            
            assertThat(rows).hasSize(2)
            assertThat(rows.map { it.receiptId }).containsExactly("p1", "p2")
        }
    }

    @Test
    fun wasteValueRows_filtersByStatus() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)

            // POSTED
            db.wasteDao().insert(createWaste("w1", restId, "ing1", "opt1", 1000L, DocumentStatus.POSTED.name))
            movementDao.insert(createWasteMovement("m1", restId, "ing1", "w1", "10.0"))
            
            // DRAFT
            db.wasteDao().insert(createWaste("w2", restId, "ing1", "opt1", 1000L, DocumentStatus.DRAFT.name))
            movementDao.insert(createWasteMovement("m2", restId, "ing1", "w2", "5.0"))
            
            // VOIDED
            db.wasteDao().insert(createWaste("w3", restId, "ing1", "opt1", 1000L, DocumentStatus.VOIDED.name))
            movementDao.insert(createWasteMovement("m3", restId, "ing1", "w3", "7.0"))

            val rows = movementDao.observeWasteValueRows(restId, 500L, 1500L).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].wasteEventId).isEqualTo("w1")
        }
    }

    private fun createReceipt(id: String, restId: String, date: Long, status: String) = PurchaseReceiptEntity(
        id, restId, null, null, date, status, null, null, 0L, 0L, if(status=="POSTED") date else null, null
    )
    
    private fun createLine(id: String, receiptId: String, total: String) = PurchaseLineEntity(
        id, receiptId, "ing1", "area1", "opt1", "1", "1", total, "1", null, 0L, 0L
    )
    
    private fun createWaste(id: String, restId: String, ingId: String, optId: String, date: Long, status: String) = WasteEventEntity(
        id, restId, ingId, "area1", optId, "1", "1", "SPOILED", date, null, null, status, 0L, 0L, if(status=="POSTED") date else null, null
    )
    
    private fun createWasteMovement(id: String, restId: String, ingId: String, docId: String, value: String) = InventoryMovementEntity(
        id, restId, ingId, "area1", InventoryMovementType.WASTE.name, "-1", "1", value, 0L, SourceDocumentType.WASTE_EVENT.name, docId, "op1", id, null, 0L
    )
}
