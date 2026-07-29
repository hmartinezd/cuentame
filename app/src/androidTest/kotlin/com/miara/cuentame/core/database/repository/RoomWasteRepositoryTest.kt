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
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomWasteRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomWasteRepository
    private val restId = RestaurantId("rest-1")
    private val activeRestaurantProvider = mockk<ActiveRestaurantProvider>()
    private val snapshotService = mockk<InventorySnapshotService>()
    private val historyValidator = mockk<WasteMovementHistoryValidator>(relaxed = true)
    private val projectionRebuilder = mockk<RoomInventoryProjectionRebuilder>(relaxed = true)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        val restEntity = RestaurantEntity(restId.value, "R", "USD", "en", 0L, 0L, null)
        coEvery { activeRestaurantProvider.getActiveRestaurant() } returns restEntity

        repository = RoomWasteRepository(
            db,
            db.wasteDao(),
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
            mockk<IntegrationFailureBoundary>(relaxed = true),
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
    fun createWasteDraft_insertsDraft() = runBlocking {
        val ingId = IngredientId("i1")
        val areaId = InventoryAreaId("a1")
        val optId = IngredientUnitOptionId("o1")
        
        db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))
        db.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingId.value, restId.value, "I", "i", null, "u1", areaId.value, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(optId.value, ingId.value, "O", "o", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))

        val command = CreateWasteDraftCommand(restId, ingId, areaId, optId, BigDecimal.ONE, WasteReason.SPOILED, Instant.now(), null, null)
        val eventId = repository.createDraft(command)
        
        val loaded = repository.getById(eventId)
        assertThat(loaded?.ingredientId).isEqualTo(ingId)
        assertThat(loaded?.status).isEqualTo(DocumentStatus.DRAFT)
    }

    @Test
    fun post_updatesStatusAndInsertsMovement() = runBlocking {
        val ingId = IngredientId("i1")
        val areaId = InventoryAreaId("a1")
        val optId = IngredientUnitOptionId("o1")
        
        db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))
        db.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingId.value, restId.value, "I", "i", null, "u1", areaId.value, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(optId.value, ingId.value, "O", "o", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))

        val command = CreateWasteDraftCommand(restId, ingId, areaId, optId, BigDecimal.ONE, WasteReason.SPOILED, Instant.now(), null, null)
        val eventId = repository.createDraft(command)

        coEvery { snapshotService.calculateAt(any(), any(), any(), any()) } returns InventorySnapshot(
            hasEffectiveHistory = true,
            areaQuantityBase = BigDecimal("10"),
            ingredientAverageCostBase = BigDecimal("2.5")
        )

        repository.post(eventId)
        
        val posted = repository.getById(eventId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        
        val movements = db.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", eventId.value)
        assertThat(movements).hasSize(1)
        assertThat(movements[0].quantityBaseSigned).isEqualTo("-1.0")
    }
}
