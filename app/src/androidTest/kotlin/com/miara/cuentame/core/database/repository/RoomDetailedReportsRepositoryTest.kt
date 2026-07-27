package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.service.ReportingPeriod
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
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomDetailedReportsRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomDetailedReportsRepository
    private val restId = RestaurantId("rest-1")

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

    private suspend fun seedDependencies() {
        db.restaurantDao().insert(RestaurantEntity(restId.value, "Rest", "USD", "en", 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", restId.value, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area2", restId.value, "Area 2", "area 2", 2, true, 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("ing1", restId.value, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing2", restId.value, "Ing 2", "ing 2", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt2", "ing2", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun observeInventoryDetails_aggregatesAreasAndCalculatesValue() = runBlocking {
        seedDependencies()
        
        // ing1: 10 in area1, 5 in area2. Cost 2.0. Total 15, Value 30.0
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area2", "5.0", 1000L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "2.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        
        assertThat(report.rows).hasSize(1)
        val row = report.rows[0]
        assertThat(row.ingredientName).isEqualTo("Ing 1")
        assertThat(row.totalQuantityBase).isEqualTo(BigDecimal("15.0"))
        assertThat(row.currentInventoryValue).isEqualTo(BigDecimal("30.00")) // Using BigDecimal(15.0).multiply(BigDecimal(2.0)) with mathContext
        assertThat(row.stockedAreaCount).isEqualTo(2)
        assertThat(report.totalValue).isEqualTo(BigDecimal("30.00"))
    }

    @Test
    fun observeInventoryDetails_missingCost_remainsNull() = runBlocking {
        seedDependencies()
        
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        
        assertThat(report.rows[0].currentInventoryValue).isNull()
        assertThat(report.rows[0].isMissingCost).isTrue()
        assertThat(report.missingCostCount).isEqualTo(1)
    }

    @Test
    fun observeInventoryDetails_negativeBalances() = runBlocking {
        seedDependencies()
        
        // ing1: -10 in area1, 5 in area2. Total -5.
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "-10.0", 1000L))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area2", "5.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        
        assertThat(report.rows[0].totalQuantityBase).isEqualTo(BigDecimal("-5.0"))
        assertThat(report.rows[0].negativeAreaBalanceCount).isEqualTo(1)
        assertThat(report.negativeBalanceCount).isEqualTo(1)
    }

    @Test
    fun observeInventoryDetails_ordering() = runBlocking {
        seedDependencies()
        
        // ing1: Missing cost
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "1.0", 1000L))
        
        // ing2: Has cost, has negative area balance
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing2", "area1", "-1.0", 1000L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing2", "10.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        
        // Order: missing cost first, then negative area balance
        assertThat(report.rows[0].ingredientId.value).isEqualTo("ing1")
        assertThat(report.rows[1].ingredientId.value).isEqualTo("ing2")
    }

    @Test
    fun observePurchaseDetails_aggregatesLines() = runBlocking {
        seedDependencies()
        
        val receipt = PurchaseReceiptEntity("p1", restId.value, null, null, 1000L, DocumentStatus.POSTED.name, null, null, 0L, 0L, 1000L, null)
        db.purchaseDao().insertReceipt(receipt)
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing1", "area1", "opt1", "1", "1", "100.0", "1", null, 0L, 0L))
        db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p1", "ing2", "area1", "opt2", "1", "1", "50.0", "1", null, 0L, 0L))
        
        val period = ReportingPeriod(Instant.ofEpochMilli(500), Instant.ofEpochMilli(1500))
        val report = repository.observePurchaseDetails(restId, period).first()
        
        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].total).isEqualTo(BigDecimal("150.0"))
        assertThat(report.rows[0].lineCount).isEqualTo(2)
        assertThat(report.totalSpend).isEqualTo(BigDecimal("150.0"))
    }

    @Test
    fun observeWasteDetails_usesHistoricalValue() = runBlocking {
        seedDependencies()
        
        val waste = WasteEventEntity("w1", restId.value, "ing1", "area1", "opt1", "10", "1", "SPOILED", 1000L, null, null, DocumentStatus.POSTED.name, 0L, 0L, 1000L, null)
        db.wasteDao().insert(waste)
        
        // Historical value 50.0
        val movement = InventoryMovementEntity("m1", restId.value, "ing1", "area1", InventoryMovementType.WASTE.name, "-10", "1", "50.0", 0L, SourceDocumentType.WASTE_EVENT.name, "w1", "op1", "m1", null, 0L)
        db.inventoryMovementDao().insert(movement)
        
        // Current cost 100.0 (should be ignored)
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "100.0", 1000L))
        
        val period = ReportingPeriod(Instant.ofEpochMilli(500), Instant.ofEpochMilli(1500))
        val report = repository.observeWasteDetails(restId, period).first()
        
        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].historicalValue).isEqualTo(BigDecimal("50.0"))
        assertThat(report.totalWasteValue).isEqualTo(BigDecimal("50.0"))
    }
}
