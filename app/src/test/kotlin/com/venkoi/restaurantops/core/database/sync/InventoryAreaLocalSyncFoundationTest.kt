package com.venkoi.restaurantops.core.database.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.UuidIdGenerator
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.SyncCursorEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import com.venkoi.restaurantops.core.database.repository.RoomInventoryAreaRepository
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class InventoryAreaLocalSyncFoundationTest {
    private lateinit var db: RestaurantInventoryDatabase
    private val json = Json { encodeDefaults = true }
    private val ids = QueueIds()
    private lateinit var writer: InventoryAreaSyncOutboxWriter
    private lateinit var repository: RoomInventoryAreaRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        writer = InventoryAreaSyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), ids, FixedTime, json
        )
        repository = RoomInventoryAreaRepository(
            db, db.inventoryAreaDao(), db.restaurantDao(), db.productionBatchDao(), writer
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `save snapshots immutable FIFO payload and known base version`() = runTest {
        restaurant()
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        repository.save(area(name = " First ", updatedAt = 10))
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "area-1", "restaurant-1", 5, 20)
        )
        repository.save(area(name = "Second", updatedAt = 11))

        val operations = db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "area-1")
        assertThat(operations).hasSize(2)
        assertThat(operations[0].localSequence).isLessThan(operations[1].localSequence)
        assertThat(operations[0].operationId).isNotEqualTo(operations[1].operationId)
        assertUuidV4(operations[0].operationId)
        assertThat(operations[0].baseServerVersion).isEqualTo(0)
        assertThat(operations[1].baseServerVersion).isEqualTo(5)
        val first = json.decodeFromString<InventoryAreaSyncPayload>(operations[0].payloadJson)
        val second = json.decodeFromString<InventoryAreaSyncPayload>(operations[1].payloadJson)
        assertThat(first.name).isEqualTo(" First ")
        assertThat(first.normalizedName).isEqualTo("first")
        assertThat(first.updatedAt).isEqualTo(10)
        assertThat(second.name).isEqualTo("Second")
    }

    @Test fun `archive snapshots tombstone and failed validation emits nothing`() = runTest {
        restaurant()
        db.inventoryAreaDao().upsert(entity("area-1", 0))
        db.inventoryAreaDao().upsert(entity("area-2", 1))
        ids.add(UUID.randomUUID().toString())

        repository.archive(InventoryAreaId("area-1"), Instant.ofEpochMilli(99))

        val operation = db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "area-1").single()
        val payload = json.decodeFromString<InventoryAreaSyncPayload>(operation.payloadJson)
        assertThat(payload.isActive).isFalse()
        assertThat(payload.deletedAt).isEqualTo(99)
        assertThat(db.inventoryAreaDao().getById("area-1")?.deletedAt).isEqualTo(99)

        runCatching { repository.archive(InventoryAreaId("area-2"), Instant.ofEpochMilli(100)) }
        assertThat(db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "area-2")).isEmpty()
    }

    @Test fun `reorder emits only changed resulting snapshots`() = runTest {
        restaurant()
        db.inventoryAreaDao().upsert(entity("area-1", 0))
        db.inventoryAreaDao().upsert(entity("area-2", 1))
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString())

        repository.reorder(listOf(InventoryAreaId("area-2"), InventoryAreaId("area-1")))

        val operations = db.syncOutboxDao().getPending("restaurant-1", INVENTORY_AREA_ENTITY_TYPE, 10)
        assertThat(operations).hasSize(2)
        assertThat(operations.map { json.decodeFromString<InventoryAreaSyncPayload>(it.payloadJson).sortOrder })
            .containsExactly(0, 1).inOrder()
    }

    @Test fun `outbox failure rolls back business mutation`() = runTest {
        restaurant()
        val repeated = UUID.randomUUID().toString()
        ids.add(repeated, repeated)
        repository.save(area(id = "area-1", name = "One"))

        val failure = runCatching { repository.save(area(id = "area-2", name = "Two")) }

        assertThat(failure.isFailure).isTrue()
        assertThat(db.inventoryAreaDao().getById("area-2")).isNull()
    }

    @Test fun `real outbox writer generates UUID v4 operation id`() = runTest {
        restaurant()
        val realWriter = InventoryAreaSyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), UuidIdGenerator(), FixedTime, json
        )
        realWriter.record(entity("area-1", 0))
        assertUuidV4(
            db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "area-1").single().operationId
        )
    }

    @Test fun `metadata and entity-scoped cursors round trip and absence is zero`() = runTest {
        val metadata = SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "area-1", "r", 5, 9)
        db.syncEntityMetadataDao().upsert(metadata)
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "area-1"))
            .isEqualTo(metadata)
        val store = SyncCursorStore(db.syncCursorDao())
        assertThat(store.getChangeSeq("r", INVENTORY_AREA_ENTITY_TYPE)).isEqualTo(0)
        db.syncCursorDao().upsert(SyncCursorEntity("r", INVENTORY_AREA_ENTITY_TYPE, 12))
        db.syncCursorDao().upsert(SyncCursorEntity("r", "OTHER", 40))
        db.syncCursorDao().upsert(SyncCursorEntity("other-r", INVENTORY_AREA_ENTITY_TYPE, 50))
        assertThat(store.getChangeSeq("r", INVENTORY_AREA_ENTITY_TYPE)).isEqualTo(12)
        assertThat(store.getChangeSeq("r", "OTHER")).isEqualTo(40)
        assertThat(store.getChangeSeq("other-r", INVENTORY_AREA_ENTITY_TYPE)).isEqualTo(50)
    }

    @Test fun `preparation is idempotent and skips metadata or pending entities`() = runTest {
        restaurant()
        db.inventoryAreaDao().upsert(entity("needs-op", 0))
        db.inventoryAreaDao().upsert(entity("has-metadata", 1))
        db.inventoryAreaDao().upsert(entity("has-pending", 2))
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "has-metadata", "restaurant-1", 1, 1)
        )
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        writer.record(entity("has-pending", 2))
        val preparation = InventoryAreaSyncPreparation(db, writer)

        preparation.prepareUnsyncedInventoryAreas("restaurant-1")
        preparation.prepareUnsyncedInventoryAreas("restaurant-1")

        assertThat(db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "needs-op")).hasSize(1)
        assertThat(db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "has-metadata")).isEmpty()
        assertThat(db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "has-pending")).hasSize(1)
    }

    private suspend fun restaurant() = db.restaurantDao().insert(
        RestaurantEntity("restaurant-1", "Restaurant", "USD", "en-US", 1, 1, null)
    )

    private fun entity(id: String, order: Int) = InventoryAreaEntity(
        id, "restaurant-1", id, id, order, true, 1, 1, null
    )

    private fun area(id: String = "area-1", name: String, updatedAt: Long = 1) = InventoryArea(
        InventoryAreaId(id), RestaurantId("restaurant-1"), name, "stale", 0, true,
        Instant.ofEpochMilli(1), Instant.ofEpochMilli(updatedAt), null
    )

    private fun assertUuidV4(value: String) {
        val uuid = UUID.fromString(value)
        assertThat(uuid.version()).isEqualTo(4)
    }

    private class QueueIds : IdGenerator {
        private val values = ArrayDeque<String>()
        fun add(vararg value: String) = values.addAll(value)
        override fun newId(): String = values.removeFirst()
    }

    private object FixedTime : TimeProvider {
        override fun now(): Instant = Instant.ofEpochMilli(1234)
    }
}
