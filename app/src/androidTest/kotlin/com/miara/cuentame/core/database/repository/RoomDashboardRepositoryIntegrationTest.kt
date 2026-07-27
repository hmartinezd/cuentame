package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.inventory.DocumentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomDashboardRepositoryIntegrationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomDashboardRepository
    
    private val now = Instant.parse("2024-01-31T12:00:00Z")
    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = now
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        val calculator = ReportingPeriodCalculator(timeProvider)
        
        repository = RoomDashboardRepository(
            db.inventoryProjectionDao(),
            db.purchaseDao(),
            db.inventoryMovementDao(),
            db.stockCountDao(),
            db.ingredientDao(),
            calculator
        )
        
        runBlocking {
            db.restaurantDao().insert(RestaurantEntity("rest-1", "Rest 1", "USD", "en", 0L, 0L, null))
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seedIngDependencies(restId: String) {
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area1", restId, "Area 1", "area 1", 1, true, 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("ing1", restId, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", "ing1", "opt", "opt", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
    }

    @Test
    fun observeDashboard_reflectsValuationChanges() {
        runBlocking {
            seedIngDependencies("rest-1")
            
            repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
                var snapshot = awaitItem()
                assertThat(snapshot.inventory.totalValue).isEqualTo(BigDecimal.ZERO)
                
                // Insert inventory and cost for ing1 (which was seeded)
                db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity("rest-1", "ing1", "area1", "10.0", 0L))
                db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity("rest-1", "ing1", "2.5", 0L))
                
                // May have multiple intermediate items due to multiple DAO updates
                snapshot = awaitItem()
                while (snapshot.inventory.totalValue == BigDecimal.ZERO) {
                    snapshot = awaitItem()
                }
                
                // 10.0 * 2.5 = 25.0
                assertThat(snapshot.inventory.totalValue.compareTo(BigDecimal("25"))).isEqualTo(0)
                assertThat(snapshot.inventory.stockedIngredientCount).isEqualTo(1)
                assertThat(snapshot.inventory.valuedIngredientCount).isEqualTo(1)
                
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun observeDashboard_excludesVoidedPurchaseFromSpend() {
        runBlocking {
            seedIngDependencies("rest-1")

            repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
                var snapshot = awaitItem()
                assertThat(snapshot.purchases.current).isEqualTo(BigDecimal.ZERO)
                
                // Insert POSTED purchase
                val pid = "p1"
                db.purchaseDao().insertReceipt(PurchaseReceiptEntity(pid, "rest-1", null, null, now.minusSeconds(3600).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, 0L, null))
                db.purchaseDao().insertLine(PurchaseLineEntity("l1", pid, "ing1", "area1", "opt1", "1", "1", "100.0", "1", null, 0L, 0L))
                
                snapshot = awaitItem()
                while (snapshot.purchases.current == BigDecimal.ZERO) {
                    snapshot = awaitItem()
                }
                assertThat(snapshot.purchases.current.compareTo(BigDecimal("100"))).isEqualTo(0)
                
                // Void it
                db.purchaseDao().updateReceipt(db.purchaseDao().getReceiptById(pid)!!.copy(status = DocumentStatus.VOIDED.name, voidedAt = now.toEpochMilli()))
                
                snapshot = awaitItem()
                while (snapshot.purchases.current != BigDecimal.ZERO) {
                    snapshot = awaitItem()
                }
                assertThat(snapshot.purchases.current).isEqualTo(BigDecimal.ZERO)
                
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
