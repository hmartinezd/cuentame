package com.venkoi.cuentame.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.StockCountStatus
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class DashboardDaoTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var inventoryProjectionDao: InventoryProjectionDao
    private lateinit var purchaseDao: PurchaseDao
    private lateinit var movementDao: InventoryMovementDao
    private lateinit var stockCountDao: StockCountDao
    private lateinit var ingredientDao: IngredientDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        inventoryProjectionDao = db.inventoryProjectionDao()
        purchaseDao = db.purchaseDao()
        movementDao = db.inventoryMovementDao()
        stockCountDao = db.stockCountDao()
        ingredientDao = db.ingredientDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedDependencies(restId: String) {
        db.restaurantDao().insert(RestaurantEntity(restId, "Rest", "USD", "en", 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", restId, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("ing1", restId, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun valuationRows_isolatesByRestaurant() {
        runBlocking {
            val rest1 = "rest-1"
            val rest2 = "rest-2"
            val ing1 = "ing-1"
            
            db.restaurantDao().insert(RestaurantEntity(rest1, "Rest 1", "USD", "en", 0L, 0L, null))
            db.restaurantDao().insert(RestaurantEntity(rest2, "Rest 2", "USD", "en", 0L, 0L, null))
            
            // Seed dependencies for JOINs
            db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
            db.ingredientDao().insert(IngredientEntity(ing1, rest1, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))

            inventoryProjectionDao.upsert(InventoryBalanceProjectionEntity(rest1, ing1, "area-1", "10.0", 1000L))
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(rest2, ing1, "50.0", 1000L))
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(rest1, ing1, "2.0", 1000L))

            val rows = inventoryProjectionDao.observeValuationRows(rest1).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].averageUnitCostBase).isEqualTo("2.0")
        }
    }

    @Test
    fun spendRows_filtersByStatusAndDate() {
        runBlocking {
            val rest1 = "rest-1"
            seedDependencies(rest1)
            
            purchaseDao.insertReceipt(createReceipt("p1", rest1, 1000L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l1", "p1", "100.0"))
            
            purchaseDao.insertReceipt(createReceipt("p2", rest1, 1100L, DocumentStatus.DRAFT.name))
            purchaseDao.insertLine(createLine("l2", "p2", "50.0"))
            
            purchaseDao.insertReceipt(createReceipt("p3", rest1, 500L, DocumentStatus.POSTED.name))
            purchaseDao.insertLine(createLine("l3", "p3", "75.0"))

            val rows = purchaseDao.observeSpendRows(rest1, 1000L, 1500L).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].receiptId).isEqualTo("p1")
            assertThat(rows[0].lineTotal).isEqualTo("100.0")
        }
    }

    @Test
    fun wasteValueRows_filtersByStatusAndDate() {
        runBlocking {
            val rest1 = "rest-1"
            seedDependencies(rest1)

            db.wasteDao().insert(createWaste("w1", rest1, "ing1", "opt1", 1000L, DocumentStatus.POSTED.name))
            movementDao.insert(createWasteMovement("m1", rest1, "ing1", "w1", "10.0"))
            
            db.wasteDao().insert(createWaste("w2", rest1, "ing1", "opt1", 1100L, DocumentStatus.VOIDED.name))
            movementDao.insert(createWasteMovement("m2", rest1, "ing1", "w2", "5.0"))

            val rows = movementDao.observeWasteValueRows(rest1, 1000L, 1500L).first()
            
            assertThat(rows).hasSize(1)
            assertThat(rows[0].wasteEventId).isEqualTo("w1")
        }
    }

    @Test
    fun stockCountSummaries_independentOfLines() {
        runBlocking {
            val rest1 = "rest-1"
            seedDependencies(rest1)
            
            stockCountDao.insertCount(createCount("c1", rest1, 1000L, StockCountStatus.COMPLETED.name))
            stockCountDao.insertCount(createCount("c2", rest1, 1100L, StockCountStatus.COMPLETED.name))
            stockCountDao.insertCountAreas(listOf(StockCountAreaEntity("ca2", "c2", "area1", "COMPLETED", 1100L, 1100L, 1)))
            stockCountDao.insertCount(createCount("c3", rest1, 1200L, StockCountStatus.DRAFT.name))

            val summaries = stockCountDao.observeCompletedCountSummaries(rest1, 1000L, 1500L).first()
            
            assertThat(summaries).hasSize(2)
            assertThat(summaries.map { it.stockCountId }).containsExactly("c1", "c2")
        }
    }

    @Test
    fun adjustedLineCount_providesPersistedValues() {
        runBlocking {
            val rest1 = "rest-1"
            seedDependencies(rest1)
            
            stockCountDao.insertCount(createCount("c1", rest1, 1000L, StockCountStatus.COMPLETED.name))
            stockCountDao.insertCountAreas(listOf(StockCountAreaEntity("ca1", "c1", "area1", "COMPLETED", 1000L, 1000L, 1)))
            
            stockCountDao.insertCountLine(StockCountLineEntity("l1", "ca1", "ing1", "opt1", "10", "10", "5", "5", null, 0L, 0L))
            
            val lines = stockCountDao.observeCompletedCountLines(rest1, 500L, 1500L).first()
            
            assertThat(lines).hasSize(1)
            assertThat(lines[0].adjustmentQuantityBase).isEqualTo("5")
        }
    }

    @Test
    fun recentActivity_deterministicOrdering() {
        runBlocking {
            val restId = "rest-1"
            seedDependencies(restId)
            
            // 1. Purchases with SAME postedAt
            val now = 1000L
            db.purchaseDao().insertReceipt(createReceipt("p2", restId, now, DocumentStatus.POSTED.name).copy(postedAt = now))
            db.purchaseDao().insertLine(createLine("l2", "p2", "10"))
            db.purchaseDao().insertReceipt(createReceipt("p1", restId, now, DocumentStatus.POSTED.name).copy(postedAt = now))
            db.purchaseDao().insertLine(createLine("l1", "p1", "10"))

            val purchases = db.purchaseDao().observeRecentPurchaseActivity(restId, 10).first()
            assertThat(purchases[0].id).isEqualTo("p1") // id ASC
            assertThat(purchases[1].id).isEqualTo("p2")

            // 2. Waste with SAME timestamp (effectiveAt)
            db.wasteDao().insert(createWaste("w2", restId, "ing1", "opt1", now, DocumentStatus.POSTED.name))
            movementDao.insert(createWasteMovement("m2", restId, "ing1", "w2", "10"))
            db.wasteDao().insert(createWaste("w1", restId, "ing1", "opt1", now, DocumentStatus.POSTED.name))
            movementDao.insert(createWasteMovement("m1", restId, "ing1", "w1", "10"))

            val waste = movementDao.observeRecentWasteActivity(restId, 10).first()
            assertThat(waste[0].id).isEqualTo("w1") // id ASC
            assertThat(waste[1].id).isEqualTo("w2")

            // 3. Stock Counts with SAME completedAt
            stockCountDao.insertCount(createCount("c2", restId, now, StockCountStatus.COMPLETED.name))
            stockCountDao.insertCount(createCount("c1", restId, now, StockCountStatus.COMPLETED.name))

            val counts = stockCountDao.observeRecentCountActivity(restId, 10).first()
            assertThat(counts[0].id).isEqualTo("c1") // id ASC
            assertThat(counts[1].id).isEqualTo("c2")
        }
    }

    private fun createReceipt(id: String, restId: String, date: Long, status: String) = PurchaseReceiptEntity(
        id, restId, null, null, date, status, null, null, null, 0L, 0L, if(status=="POSTED") date else null, null
    )
    
    private fun createLine(id: String, receiptId: String, total: String) = PurchaseLineEntity(
        id, receiptId, "ing1", "area1", "opt1", "1", "1", total, "1", null, 0L, 0L
    )
    
    private fun createWaste(id: String, restId: String, ingId: String, optId: String, date: Long, status: String) = WasteEventEntity(
        id, restId, ingId, "area1", optId, "1", "1", "SPOILED", date, null, null, null, status, 0L, 0L, if(status=="POSTED") date else null, null
    )
    
    private fun createWasteMovement(id: String, restId: String, ingId: String, docId: String, value: String) = InventoryMovementEntity(
        id, restId, ingId, "area1", InventoryMovementType.WASTE.name, "-1", "1", value, 0L, SourceDocumentType.WASTE_EVENT.name, docId, "op1", id, null, 0L
    )

    private fun createCount(id: String, restId: String, date: Long, status: String) = StockCountEntity(
        id, restId, "Count", date, date, if(status=="COMPLETED") date else null, status, null, 0L, 0L, null
    )
}
