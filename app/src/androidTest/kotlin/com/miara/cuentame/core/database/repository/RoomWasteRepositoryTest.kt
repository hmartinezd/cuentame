package com.miara.cuentame.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class RoomWasteRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomWasteRepository
    
    private val timeProvider = object : TimeProvider {
        var now = Instant.parse("2024-01-01T10:00:00Z")
        override fun now() = now
    }

    private val idGenerator = object : IdGenerator {
        var nextId = 1
        override fun newId() = "id-${nextId++}"
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()

        val movementValidator = InventoryMovementValidator()
        val historyValidator = WasteMovementHistoryValidator(movementValidator)
        val costCalculator = WeightedAverageCostCalculator()
        val snapshotService = RoomInventorySnapshotService(db.inventoryMovementDao(), costCalculator, movementValidator)
        val projectionRebuilder = RoomInventoryProjectionRebuilder(
            db, db.ingredientDao(), db.inventoryMovementDao(), db.inventoryProjectionDao(), 
            db.ingredientCostProjectionDao(), costCalculator, timeProvider
        )

        repository = RoomWasteRepository(
            db, db.wasteDao(), db.inventoryMovementDao(), db.ingredientDao(), 
            db.inventoryAreaDao(), db.ingredientUnitOptionDao(), db.restaurantDao(),
            snapshotService, historyValidator, projectionRebuilder, idGenerator, timeProvider
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createDraft_persistsData() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val command = CreateWasteDraftCommand(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = unitOptionId,
            quantityEntered = BigDecimal("5.0"),
            reason = WasteReason.SPOILED,
            effectiveAt = timeProvider.now(),
            notes = "test notes",
            attachmentUri = "test-uri"
        )

        val id = repository.createDraft(command)
        val event = repository.getById(id)

        assertThat(event).isNotNull()
        assertThat(event!!.id).isEqualTo(id)
        assertThat(event.quantityEntered.compareTo(BigDecimal("5.0"))).isEqualTo(0)
        assertThat(event.status).isEqualTo(DocumentStatus.DRAFT)
        assertThat(event.notes).isEqualTo("test notes")
        assertThat(event.attachmentPath).isEqualTo("test-uri")
    }

    @Test
    fun post_createsMovementAndUpdatesProjections() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val command = CreateWasteDraftCommand(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = unitOptionId,
            quantityEntered = BigDecimal("3.0"),
            reason = WasteReason.SPOILED,
            effectiveAt = timeProvider.now(),
            notes = null,
            attachmentUri = null
        )

        val id = repository.createDraft(command)
        repository.post(id)

        val event = repository.getById(id)
        assertThat(event!!.status).isEqualTo(DocumentStatus.POSTED)
        assertThat(event.postedAt).isNotNull()

        val movements = db.inventoryMovementDao().getByIngredient(ingredientId.value)
        assertThat(movements).hasSize(1)
        assertThat(BigDecimal(movements[0].quantityBaseSigned).compareTo(BigDecimal("-3.0"))).isEqualTo(0)
        
        val projection = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)
        assertThat(BigDecimal(projection!!.quantityBase).compareTo(BigDecimal("-3.0"))).isEqualTo(0)
    }

    private suspend fun setupRestaurant(): RestaurantId {
        val id = "rest-1"
        db.restaurantDao().insert(
            RestaurantEntity(id, "Test Rest", "USD", "en", 0L, 0L, null)
        )
        return RestaurantId(id)
    }

    private suspend fun setupArea(restaurantId: RestaurantId): InventoryAreaId {
        val id = "area-1"
        db.inventoryAreaDao().upsert(
            InventoryAreaEntity(id, restaurantId.value, "Main Area", "main area", 1, true, 0L, 0L, null)
        )
        return InventoryAreaId(id)
    }

    private suspend fun setupBaseUnit(): String {
        val id = "unit-1"
        db.unitDao().insertSeedUnits(
            listOf(UnitEntity(id, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1))
        )
        return id
    }

    private suspend fun setupIngredient(restaurantId: RestaurantId, unitId: String): IngredientId {
        val id = "ing-1"
        db.ingredientDao().insert(
            IngredientEntity(id, restaurantId.value, "Chicken", "chicken", null, unitId, null, null, null, null, true, 0L, 0L, null)
        )
        return IngredientId(id)
    }

    private suspend fun setupUnitOption(ingredientId: IngredientId): IngredientUnitOptionId {
        val id = "opt-1"
        db.ingredientUnitOptionDao().insert(
            IngredientUnitOptionEntity(id, ingredientId.value, "lb", "lb", null, BigDecimal.ONE, true, false, false, true, 0L, 0L, null)
        )
        return IngredientUnitOptionId(id)
    }
}
