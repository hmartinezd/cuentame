package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.model.inventory.StockCountStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomStockCountRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomStockCountRepository
    private val restId = RestaurantId("rest-1")
    private val activeRestaurantProvider = mockk<ActiveRestaurantProvider>()
    private val snapshotService = mockk<InventorySnapshotService>()
    private val historyValidator = mockk<StockCountMovementHistoryValidator>(relaxed = true)
    private val projectionRebuilder = mockk<RoomInventoryProjectionRebuilder>(relaxed = true)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        val restEntity = RestaurantEntity(restId.value, "R", "USD", "en", 0L, 0L, null)
        coEvery { activeRestaurantProvider.getActiveRestaurant() } returns restEntity

        repository = RoomStockCountRepository(
            db,
            db.stockCountDao(),
            db.inventoryMovementDao(),
            db.ingredientDao(),
            db.inventoryAreaDao(),
            db.ingredientUnitOptionDao(),
            db.restaurantDao(),
            snapshotService,
            historyValidator,
            projectionRebuilder,
            object : IdGenerator { override fun newId(): String = "id" },
            object : TimeProvider { override fun now(): Instant = Instant.now() },
            activeRestaurantProvider
        )
        runBlocking {
            db.restaurantDao().insert(restEntity)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startStockCount_insertsDraft() = runBlocking {
        val areaId = InventoryAreaId("a1")
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))

        val command = StartStockCountCommand(restId, "New Count", Instant.now(), listOf(areaId), null)
        val countId = repository.start(command)
        
        val details = repository.observeCount(countId).first()
        val loaded = details?.count
        
        assertThat(loaded?.name).isEqualTo("New Count")
        assertThat(loaded?.status).isEqualTo(StockCountStatus.DRAFT)
    }

    @Test
    fun completeCount_updatesStatusAndInsertsMovements() = runBlocking {
        val areaId = InventoryAreaId("a1")
        val ingId = IngredientId("i1")
        val optId = IngredientUnitOptionId("o1")
        
        db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))
        db.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingId.value, restId.value, "I", "i", null, "u1", areaId.value, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(optId.value, ingId.value, "O", "o", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))

        val countId = repository.start(StartStockCountCommand(restId, "C", Instant.now(), listOf(areaId), null))
        val area = repository.observeCount(countId).first()!!.areas.first()
        
        repository.saveLine(com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand(
            countId, area.area.id, null, ingId, optId, BigDecimal("10"), null
        ))
        repository.completeArea(countId, area.area.id)

        coEvery { snapshotService.calculateAt(any(), any(), any(), any()) } returns InventorySnapshot(
            hasEffectiveHistory = false,
            areaQuantityBase = BigDecimal.ZERO,
            ingredientAverageCostBase = null
        )

        repository.completeCount(countId)
        
        val finished = repository.observeCount(countId).first()!!.count
        assertThat(finished.status).isEqualTo(StockCountStatus.COMPLETED)
        
        val movements = db.inventoryMovementDao().getBySourceDocument("STOCK_COUNT", countId.value)
        assertThat(movements).hasSize(1)
    }
}
