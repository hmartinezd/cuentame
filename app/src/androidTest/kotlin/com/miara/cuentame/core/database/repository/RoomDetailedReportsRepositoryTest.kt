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
import com.miara.cuentame.core.domain.service.ReportingPeriod
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
        db.restaurantDao().insert(RestaurantEntity(restId.value, "Test Rest", "USD", "en", 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", restId.value, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area2", restId.value, "Area 2", "area 2", 2, true, 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing1", restId.value, "Chicken", "chicken", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun observeInventoryDetails_aggregatesAreasAndCalculatesValue() = runBlocking {
        seedDependencies()
        
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area2", "5.0", 1000L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "2.0", 1000L))

        val report = repository.observeInventoryDetails(restId).first()
        
        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].totalQuantityBase).isEqualTo(BigDecimal("15.0"))
        assertThat(report.rows[0].currentInventoryValue).isEqualTo(BigDecimal("30.00"))
        assertThat(report.totalValue).isEqualTo(BigDecimal("30.00"))
    }

    @Test
    fun observeInventoryDetails_alertOnlyRow_aggregateZeroButNegativeArea() = runBlocking {
        seedDependencies()
        
        // Aggregate is 0, but area1 is -5 and area2 is +5
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "-5.0", 1000L))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area2", "5.0", 1000L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "2.0", 1000L))

        val report = repository.observeInventoryDetails(restId).first()
        
        // MUST be included as alert-only row
        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].totalQuantityBase.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(report.rows[0].negativeAreaBalanceCount).isEqualTo(1)
        
        // Stocked count only counts items with non-zero aggregate
        assertThat(report.stockedIngredientCount).isEqualTo(0)
        assertThat(report.negativeBalanceCount).isEqualTo(1)
    }

    @Test
    fun observeInventoryDetails_excludedRow_allZeroNoNegative() = runBlocking {
        seedDependencies()
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "0.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        assertThat(report.rows).isEmpty()
    }

    @Test
    fun observeInventoryDetails_missingCost_onlyForStocked() = runBlocking {
        seedDependencies()
        
        // Stocked but no cost
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        assertThat(report.rows[0].isMissingCost).isTrue()
        assertThat(report.missingCostCount).isEqualTo(1)
        
        // Not stocked (zero balance) -> missing cost should NOT be counted even if cost is null
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "0.0", 1000L))
        val report2 = repository.observeInventoryDetails(restId).first()
        assertThat(report2.rows).isEmpty()
    }

    @Test
    fun observeInventoryDetails_zeroCost_isValid() = runBlocking {
        seedDependencies()
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "0.0", 1000L))
        
        val report = repository.observeInventoryDetails(restId).first()
        assertThat(report.rows[0].isMissingCost).isFalse()
        assertThat(report.rows[0].currentInventoryValue?.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(report.valuedIngredientCount).isEqualTo(1)
    }

    @Test
    fun observeInventoryDetails_strictDecimalValidation() = runBlocking {
        seedDependencies()
        
        // Negative cost -> Error
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, "ing1", "-1.0", 1000L))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, "ing1", "area1", "10.0", 1000L))
        
        try {
            repository.observeInventoryDetails(restId).first()
            org.junit.Assert.fail("Should throw ValidationError.InvalidDecimal")
        } catch (e: com.miara.cuentame.core.domain.validation.ValidationError.InvalidDecimal) {
            // Success
        }
    }

    @Test
    fun observePurchaseDetails_strictDecimalValidation() = runBlocking {
        seedDependencies()
        val receipt = PurchaseReceiptEntity("p1", restId.value, null, null, 1000L, DocumentStatus.POSTED.name, null, null, null, 0L, 0L, 1000L, null)
        db.purchaseDao().insertReceipt(receipt)
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing1", "area1", "opt1", "1", "1", "-100.0", "1", null, 0L, 0L))
        
        val period = ReportingPeriod(Instant.ofEpochMilli(500), Instant.ofEpochMilli(1500))
        try {
            repository.observePurchaseDetails(restId, period).first()
            org.junit.Assert.fail("Should throw ValidationError.InvalidDecimal")
        } catch (e: com.miara.cuentame.core.domain.validation.ValidationError.InvalidDecimal) {
            // Success
        }
    }

    @Test
    fun observeWasteDetails_strictSnapshotValidation() = runBlocking {
        seedDependencies()
        db.wasteDao().insert(WasteEventEntity("w1", restId.value, "ing1", "area1", "opt1", "10", "1", "SPOILED", 1000L, null, null, null, DocumentStatus.POSTED.name, 0L, 0L, 1000L, null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m1", restId.value, "ing1", "area1", InventoryMovementType.WASTE.name, "-10", "1", null, 1000L, SourceDocumentType.WASTE_EVENT.name, "w1", "op1", "m1", null, 0L))
        
        val period = ReportingPeriod(Instant.ofEpochMilli(500), Instant.ofEpochMilli(1500))
        try {
            repository.observeWasteDetails(restId, period).first()
            org.junit.Assert.fail("Should throw ValidationError.MalformedInventoryMovementHistory")
        } catch (e: com.miara.cuentame.core.domain.validation.ValidationError.MalformedInventoryMovementHistory) {
            // Success
        }
    }
}
