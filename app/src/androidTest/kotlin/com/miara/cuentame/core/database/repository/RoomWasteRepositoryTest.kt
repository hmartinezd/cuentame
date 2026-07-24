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
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import kotlinx.coroutines.runBlocking
import com.miara.cuentame.core.model.inventory.SourceDocumentType

class RoomWasteRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomWasteRepository
    private var failurePoint: String? = null
    
    private val failureBoundary = object : IntegrationFailureBoundary {
        override fun trigger(point: String) {
            if (point == failurePoint) throw ForcedFailureException()
        }
    }

    private val timeProvider = object : TimeProvider {
        var now = Instant.ofEpochMilli(10000L)
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
            snapshotService, historyValidator, projectionRebuilder, idGenerator, timeProvider,
            failureBoundary
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

    @Test
    fun post_rollback_onFailureAfterMovement() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val id = repository.createDraft(CreateWasteDraftCommand(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = unitOptionId,
            quantityEntered = BigDecimal("3.0"),
            reason = WasteReason.SPOILED,
            effectiveAt = timeProvider.now(),
            notes = null,
            attachmentUri = null
        ))

        failurePoint = "post-after-movement"

        try {
            repository.post(id)
        } catch (e: ForcedFailureException) {
            // Expected
        }

        val event = repository.getById(id)
        assertThat(event!!.status).isEqualTo(DocumentStatus.DRAFT)
        assertThat(event.postedAt).isNull()

        val movements = db.inventoryMovementDao().getByIngredient(ingredientId.value)
        assertThat(movements).isEmpty()

        val projection = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)
        assertThat(projection).isNull()
    }

    @Test
    fun void_rollback_onFailureAfterReversal() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val id = repository.createDraft(CreateWasteDraftCommand(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = unitOptionId,
            quantityEntered = BigDecimal("3.0"),
            reason = WasteReason.SPOILED,
            effectiveAt = timeProvider.now(),
            notes = null,
            attachmentUri = null
        ))
        repository.post(id)

        failurePoint = "void-after-reversal"

        try {
            repository.void(id)
        } catch (e: ForcedFailureException) {
            // Expected
        }

        val event = repository.getById(id)
        assertThat(event!!.status).isEqualTo(DocumentStatus.POSTED)
        assertThat(event.voidedAt).isNull()

        val movements = db.inventoryMovementDao().getByIngredient(ingredientId.value)
        assertThat(movements).hasSize(1) // Only original WASTE
        assertThat(movements[0].movementType).isEqualTo(InventoryMovementType.WASTE.name)

        val projection = db.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)
        assertThat(BigDecimal(projection!!.quantityBase).compareTo(BigDecimal("-3.0"))).isEqualTo(0)
    }

    @Test
    fun post_revalidatesIngredientOwnership() = runTest {
        val restaurantId = setupRestaurant()
        val otherRestaurantId = RestaurantId("other-rest")
        db.restaurantDao().insert(RestaurantEntity(otherRestaurantId.value, "Other", "USD", "en", 0L, 0L, null))
        
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(otherRestaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        // Manually insert a draft that bypasses repository.createDraft validation (e.g. corrupted DB)
        db.wasteDao().insert(com.miara.cuentame.core.database.entity.WasteEventEntity(
            id = "corrupted-1",
            restaurantId = restaurantId.value,
            ingredientId = ingredientId.value,
            areaId = areaId.value,
            ingredientUnitOptionId = unitOptionId.value,
            quantityEntered = "5.0",
            quantityBase = "5.0",
            reason = WasteReason.SPOILED.name,
            effectiveAt = timeProvider.now().toEpochMilli(),
            notes = null,
            attachmentPath = null,
            status = DocumentStatus.DRAFT.name,
            createdAt = timeProvider.now().toEpochMilli(),
            updatedAt = timeProvider.now().toEpochMilli(),
            postedAt = null,
            voidedAt = null
        ))

        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.WasteIngredientOwnershipMismatch::class.java) {
            runBlocking { repository.post(WasteEventId("corrupted-1")) }
        }
    }

    @Test
    fun createDraft_rejectsFutureEffectiveTime() = runTest {
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
            effectiveAt = timeProvider.now().plusSeconds(3600),
            notes = null,
            attachmentUri = null
        )

        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.InvalidWasteEffectiveTime::class.java) {
            runBlocking { repository.createDraft(command) }
        }
    }

    @Test
    fun post_calculatesHistoricalCostCorrectly() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        // Seed a purchase at T=0
        db.inventoryMovementDao().insert(InventoryMovementEntity(
            "mov-1", restaurantId.value, ingredientId.value, areaId.value, "PURCHASE", "10.0", "2.0", "20.0",
            0L, "PURCHASE_RECEIPT", "doc-1", "line-1", "op-1", null, 0L
        ))

        // Create waste at T=1000
        val id = repository.createDraft(CreateWasteDraftCommand(
            restaurantId, ingredientId, areaId, unitOptionId, BigDecimal("2.0"), WasteReason.SPOILED,
            Instant.ofEpochMilli(1000L), null, null
        ))

        // Seed a purchase at T=2000 (later)
        db.inventoryMovementDao().insert(InventoryMovementEntity(
            "mov-2", restaurantId.value, ingredientId.value, areaId.value, "PURCHASE", "10.0", "4.0", "40.0",
            2000L, "PURCHASE_RECEIPT", "doc-2", "line-2", "op-2", null, 2000L
        ))

        repository.post(id)

        val movements = db.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", id.value)
        val wasteMov = movements.find { it.movementType == "WASTE" }!!
        
        // Snapshot should be at T=1000, cost = 2.0
        assertThat(BigDecimal(wasteMov.unitCostBaseSnapshot!!).compareTo(BigDecimal("2.0"))).isEqualTo(0)
        assertThat(BigDecimal(wasteMov.totalValueSnapshot!!).compareTo(BigDecimal("-4.0"))).isEqualTo(0)
    }

    @Test
    fun void_createsReversalAndRestoresBalance() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        // Seed 10 lb
        db.inventoryMovementDao().insert(InventoryMovementEntity("mov-1", restaurantId.value, ingredientId.value, areaId.value, "PURCHASE", "10.0", "1.0", "10.0", 0L, "PURCHASE_RECEIPT", "d1", "l1", "o1", null, 0L))

        // Post 3 lb waste
        val id = repository.createDraft(CreateWasteDraftCommand(restaurantId, ingredientId, areaId, unitOptionId, BigDecimal("3.0"), WasteReason.SPOILED, Instant.ofEpochMilli(1000L), null, null))
        repository.post(id)

        // Verify balance 7 lb
        assertThat(BigDecimal(db.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)!!.quantityBase).compareTo(BigDecimal("7.0"))).isEqualTo(0)

        // Void
        repository.void(id)

        // Verify status
        assertThat(repository.getById(id)!!.status).isEqualTo(DocumentStatus.VOIDED)

        // Verify balance 10 lb
        assertThat(BigDecimal(db.inventoryProjectionDao().getBalance(ingredientId.value, areaId.value)!!.quantityBase).compareTo(BigDecimal("10.0"))).isEqualTo(0)

        val movements = db.inventoryMovementDao().getByIngredient(ingredientId.value)
        assertThat(movements).hasSize(3) // PURCHASE, WASTE, REVERSAL
        assertThat(movements.any { it.movementType == "REVERSAL" }).isTrue()
    }

    @Test
    fun updateDraft_persistsChanges() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val id = repository.createDraft(CreateWasteDraftCommand(restaurantId, ingredientId, areaId, unitOptionId, BigDecimal("5.0"), WasteReason.SPOILED, timeProvider.now(), null, null))
        
        repository.updateDraft(com.miara.cuentame.core.domain.repository.UpdateWasteDraftCommand(
            wasteEventId = id,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = unitOptionId,
            quantityEntered = BigDecimal("10.0"),
            reason = WasteReason.EXPIRED,
            effectiveAt = timeProvider.now(),
            notes = "updated notes",
            attachmentUri = null
        ))

        val event = repository.getById(id)!!
        assertThat(event.quantityEntered.compareTo(BigDecimal("10.0"))).isEqualTo(0)
        assertThat(event.reason).isEqualTo(WasteReason.EXPIRED)
        assertThat(event.notes).isEqualTo("updated notes")
    }

    @Test
    fun post_rejectsZeroStoredQuantity() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        db.wasteDao().insert(com.miara.cuentame.core.database.entity.WasteEventEntity(
            id = "corrupted-zero",
            restaurantId = restaurantId.value,
            ingredientId = ingredientId.value,
            areaId = areaId.value,
            ingredientUnitOptionId = unitOptionId.value,
            quantityEntered = "0.0",
            quantityBase = "0.0",
            reason = WasteReason.SPOILED.name,
            effectiveAt = timeProvider.now().toEpochMilli(),
            notes = null,
            attachmentPath = null,
            status = DocumentStatus.DRAFT.name,
            createdAt = timeProvider.now().toEpochMilli(),
            updatedAt = timeProvider.now().toEpochMilli(),
            postedAt = null,
            voidedAt = null
        ))

        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.InvalidWasteQuantity::class.java) {
            runBlocking { repository.post(WasteEventId("corrupted-zero")) }
        }
    }

    @Test
    fun deleteDraft_removesFromDb() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        val id = repository.createDraft(CreateWasteDraftCommand(restaurantId, ingredientId, areaId, unitOptionId, BigDecimal("5.0"), WasteReason.SPOILED, timeProvider.now(), null, null))
        repository.deleteDraft(id)

        assertThat(repository.getById(id)).isNull()
    }

    @Test
    fun post_rejectsUnknownStoredStatus() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        db.wasteDao().insert(com.miara.cuentame.core.database.entity.WasteEventEntity(
            id = "corrupted-status",
            restaurantId = restaurantId.value,
            ingredientId = ingredientId.value,
            areaId = areaId.value,
            ingredientUnitOptionId = unitOptionId.value,
            quantityEntered = "5.0",
            quantityBase = "5.0",
            reason = WasteReason.SPOILED.name,
            effectiveAt = timeProvider.now().toEpochMilli(),
            notes = null,
            attachmentPath = null,
            status = "UNKNOWN_STATUS",
            createdAt = timeProvider.now().toEpochMilli(),
            updatedAt = timeProvider.now().toEpochMilli(),
            postedAt = null,
            voidedAt = null
        ))

        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.MalformedWasteMovementHistory::class.java) {
            runBlocking { repository.post(WasteEventId("corrupted-status")) }
        }
    }

    @Test
    fun post_rejectsInvalidReason() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        db.wasteDao().insert(com.miara.cuentame.core.database.entity.WasteEventEntity(
            id = "corrupted-reason",
            restaurantId = restaurantId.value,
            ingredientId = ingredientId.value,
            areaId = areaId.value,
            ingredientUnitOptionId = unitOptionId.value,
            quantityEntered = "5.0",
            quantityBase = "5.0",
            reason = "NOT_A_REASON",
            effectiveAt = timeProvider.now().toEpochMilli(),
            notes = null,
            attachmentPath = null,
            status = DocumentStatus.DRAFT.name,
            createdAt = timeProvider.now().toEpochMilli(),
            updatedAt = timeProvider.now().toEpochMilli(),
            postedAt = null,
            voidedAt = null
        ))

        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.InvalidWasteReason::class.java) {
            runBlocking { repository.post(WasteEventId("corrupted-reason")) }
        }
    }

    @Test
    fun void_rejectsEarlyVoidTime() = runTest {
        val restaurantId = setupRestaurant()
        val areaId = setupArea(restaurantId)
        val unitId = setupBaseUnit()
        val ingredientId = setupIngredient(restaurantId, unitId)
        val unitOptionId = setupUnitOption(ingredientId)

        // Create at T=1000
        timeProvider.now = Instant.ofEpochMilli(1000L)
        val id = repository.createDraft(CreateWasteDraftCommand(restaurantId, ingredientId, areaId, unitOptionId, BigDecimal("5.0"), WasteReason.SPOILED, timeProvider.now(), null, null))
        
        // Post at T=2000
        timeProvider.now = Instant.ofEpochMilli(2000L)
        repository.post(id)

        // Try to void at T=1500 (before post T=2000)
        timeProvider.now = Instant.ofEpochMilli(1500L)
        
        assertThrows(com.miara.cuentame.core.domain.validation.ValidationError.MalformedWasteMovementHistory::class.java) {
            runBlocking { repository.void(id) }
        }
    }

    private suspend fun setupUnitOption(ingredientId: IngredientId): IngredientUnitOptionId {
        val id = "opt-1"
        db.ingredientUnitOptionDao().insert(
            IngredientUnitOptionEntity(id, ingredientId.value, "lb", "lb", null, BigDecimal.ONE, true, false, false, true, 0L, 0L, null)
        )
        return IngredientUnitOptionId(id)
    }
}
