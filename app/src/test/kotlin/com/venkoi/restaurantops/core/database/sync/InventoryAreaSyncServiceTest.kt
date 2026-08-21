package com.venkoi.restaurantops.core.database.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.SyncCursorEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InventoryAreaSyncServiceTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var remote: FakeRemote
    private lateinit var service: InventoryAreaSyncService
    private val json = Json { encodeDefaults = true }
    private val ids = QueueIds()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        remote = FakeRemote()
        val writer = InventoryAreaSyncOutboxWriter(db.syncEntityMetadataDao(), db.syncOutboxDao(), ids, FixedTime, json)
        service = InventoryAreaSyncService(db, InventoryAreaSyncPreparation(db, writer), remote)
    }

    @After fun tearDown() = db.close()

    @Test fun `prepare unsynced area pushes immutable APPLIED operation and ack does not advance cursor`() = runTest {
        seedRestaurant()
        db.inventoryAreaDao().upsert(area("area"))
        ids.values += "operation-a"
        remote.applyHandler = { InventoryAreaRemoteApplyResult.Applied(it.entityId, 4, 12) }

        val result = service.sync(RESTAURANT)

        assertThat(result).isEqualTo(InventoryAreaSyncResult.Success(1, 0, 0))
        val sent = remote.applied.single()
        assertThat(sent.operationId).isEqualTo("operation-a")
        assertThat(sent.payloadJson).isEqualTo(json.encodeToString(payload("area")))
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "area"))
            .isEqualTo(SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "area", RESTAURANT.value, 4, 12))
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)).isNull()
    }

    @Test fun `ALREADY_APPLIED replay acknowledges same identity without duplicate`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("area")); insertOperation(1, "same-op", "area", 0)
        remote.applyHandler = { InventoryAreaRemoteApplyResult.AlreadyApplied(it.entityId, 1, 8) }

        val result = service.sync(RESTAURANT)

        assertThat(result).isEqualTo(InventoryAreaSyncResult.Success(1, 0, 0))
        assertThat(remote.applied.single().operationId).isEqualTo("same-op")
        assertThat(remote.applied.single().payloadJson).isEqualTo(json.encodeToString(payload("area")))
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
    }

    @Test fun `ambiguous failure preserves exact operation and next invocation replays it`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("area")); insertOperation(5, "durable-op", "area", 0)
        var attempt = 0
        remote.applyResultHandler = {
            if (attempt++ == 0) Result.failure(TestTransportException())
            else Result.success(InventoryAreaRemoteApplyResult.AlreadyApplied(it.entityId, 1, 2))
        }

        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.RemoteFailure)
        val retained = db.syncOutboxDao().getAll().single()
        assertThat(retained.operationId).isEqualTo("durable-op")
        assertThat(retained.payloadJson).isEqualTo(json.encodeToString(payload("area")))
        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.Success(1, 0, 0))
        assertThat(remote.applied.map { it.operationId }).containsExactly("durable-op", "durable-op").inOrder()
        assertThat(remote.applied.map { it.payloadJson }).containsExactly(retained.payloadJson, retained.payloadJson).inOrder()
    }

    @Test fun `three sequential operations are sent with rebased versions and immutable payloads`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("area"))
        insertOperation(10, "op-1", "area", 0, "one")
        insertOperation(11, "op-2", "area", 0, "two")
        insertOperation(12, "op-3", "area", 0, "three")
        remote.applyHandler = { request ->
            val version = remote.applied.size.toLong()
            InventoryAreaRemoteApplyResult.Applied(request.entityId, version, version + 20)
        }

        val result = service.sync(RESTAURANT)

        assertThat(result).isEqualTo(InventoryAreaSyncResult.Success(3, 0, 0))
        assertThat(remote.applied.map { it.baseServerVersion }).containsExactly(0L, 1L, 2L).inOrder()
        assertThat(remote.applied.map { it.operationId }).containsExactly("op-1", "op-2", "op-3").inOrder()
        assertThat(remote.applied.map { it.payloadJson }).containsExactly(
            json.encodeToString(payload("area", "one")),
            json.encodeToString(payload("area", "two")),
            json.encodeToString(payload("area", "three"))
        ).inOrder()
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "area")?.serverVersion).isEqualTo(3)
    }

    @Test fun `CONFLICT preserves operation metadata and business and never pulls`() = runTest {
        seedRestaurant(); val local = area("area", "Local"); db.inventoryAreaDao().upsert(local)
        db.syncEntityMetadataDao().upsert(SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "area", RESTAURANT.value, 2, 4))
        insertOperation(1, "op", "area", 2)
        remote.applyHandler = { InventoryAreaRemoteApplyResult.Conflict(it.entityId, 5, 9) }

        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.Conflict("area", 5, 9))
        assertThat(db.syncOutboxDao().getAll()).hasSize(1)
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "area")?.serverVersion).isEqualTo(2)
        assertThat(db.inventoryAreaDao().getById("area")).isEqualTo(local)
        assertThat(remote.pulls).isEmpty()
    }

    @Test fun `INVALID_OPERATION and remote failure preserve outbox and cursor and never pull`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("area")); insertOperation(1, "op", "area", 0)
        db.syncCursorDao().upsert(SyncCursorEntity(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, 7))
        remote.applyHandler = { InventoryAreaRemoteApplyResult.InvalidOperation(it.entityId) }
        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.InvalidOperation("area"))
        assertThat(db.syncOutboxDao().getAll()).hasSize(1)
        remote.applyResultHandler = { Result.failure(TestTransportException()) }
        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.RemoteFailure)
        assertThat(db.syncOutboxDao().getAll()).hasSize(1)
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(7)
        assertThat(remote.pulls).isEmpty()
    }

    @Test fun `empty outbox pulls from zero and applies row metadata cursor and tombstone without outbox`() = runTest {
        seedRestaurant()
        remote.pages += listOf(remoteArea("cloud", 1, isActive = false, deletedAt = 900))

        val result = service.sync(RESTAURANT)

        assertThat(remote.pulls.single().second).isEqualTo(0)
        assertThat(result).isEqualTo(InventoryAreaSyncResult.Success(0, 1, 1))
        assertThat(db.inventoryAreaDao().getById("cloud")?.isActive).isFalse()
        assertThat(db.inventoryAreaDao().getById("cloud")?.deletedAt).isEqualTo(900)
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "cloud")?.changeSeq).isEqualTo(1)
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(1)
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
    }

    @Test fun `two page pull advances cursor in order`() = runTest {
        seedRestaurant()
        remote.pages += (1L..100L).map { remoteArea("area-$it", it) }
        remote.pages += listOf(remoteArea("area-101", 101))

        val result = service.sync(RESTAURANT)

        assertThat(result).isEqualTo(InventoryAreaSyncResult.Success(0, 101, 101))
        assertThat(remote.pulls.map { it.second }).containsExactly(0L, 100L).inOrder()
    }

    @Test fun `wrong restaurant nonascending and stale pages are rejected without mutation`() = runTest {
        suspend fun assertRejected(page: List<RemoteInventoryArea>) {
            remote.pages.clear(); remote.pages += page
            assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.ProtocolFailure)
            assertThat(db.inventoryAreaDao().getAllForRestaurantSync(RESTAURANT.value)).isEmpty()
            assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)).isNull()
        }
        seedRestaurant()
        assertRejected(listOf(remoteArea("wrong", 1).copy(restaurantId = "other")))
        assertRejected(listOf(remoteArea("a", 2), remoteArea("b", 2)))
        assertRejected(listOf(remoteArea("stale", 0)))
    }

    @Test fun `local operation appearing before page transaction prevents page and cursor application`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("local"))
        db.syncEntityMetadataDao().upsert(SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "local", RESTAURANT.value, 1, 1))
        remote.pullHandler = { _, _, _ ->
            insertOperation(9, "new-local-op", "local", 1)
            listOf(remoteArea("cloud", 2))
        }

        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.LocalChangesPending)
        assertThat(db.inventoryAreaDao().getById("cloud")).isNull()
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)).isNull()
    }

    @Test fun `page database failure rolls back business metadata and cursor`() = runTest {
        seedRestaurant()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_second_metadata BEFORE INSERT ON sync_entity_metadata
            WHEN NEW.entityId = 'bad' BEGIN SELECT RAISE(ABORT, 'forced'); END
        """.trimIndent())
        remote.pages += listOf(remoteArea("good", 1), remoteArea("bad", 2))

        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.ProtocolFailure)
        assertThat(db.inventoryAreaDao().getById("good")).isNull()
        assertThat(db.inventoryAreaDao().getById("bad")).isNull()
        assertThat(db.syncEntityMetadataDao().getAll()).isEmpty()
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)).isNull()
    }

    @Test fun `successful push then pull applies own server row without new outbox and cursor comes from pull`() = runTest {
        seedRestaurant(); db.inventoryAreaDao().upsert(area("area")); insertOperation(1, "op", "area", 0)
        remote.applyHandler = { InventoryAreaRemoteApplyResult.Applied(it.entityId, 1, 22) }
        remote.pages += listOf(remoteArea("area", 22).copy(serverVersion = 1))

        assertThat(service.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.Success(1, 1, 22))
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(22)
    }

    private suspend fun seedRestaurant() {
        db.restaurantDao().insert(RestaurantEntity(RESTAURANT.value, "Restaurant", "USD", "en-US", 1, 1, null))
    }

    private suspend fun insertOperation(sequence: Long, operationId: String, entityId: String, base: Long, name: String = "Area") {
        db.syncOutboxDao().insert(SyncOutboxEntity(
            sequence, operationId, RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, entityId, base,
            json.encodeToString(payload(entityId, name)), 50
        ))
    }

    private fun area(id: String, name: String = "Area") = InventoryAreaEntity(
        id, RESTAURANT.value, name, name.lowercase(), 0, true, 100, 200, null
    )

    private fun payload(id: String, name: String = "Area") = InventoryAreaSyncPayload(
        id, RESTAURANT.value, name, name.lowercase(), 0, true, 100, 200, null
    )

    private fun remoteArea(id: String, seq: Long, isActive: Boolean = true, deletedAt: Long? = null) =
        RemoteInventoryArea(id, RESTAURANT.value, "Remote $id", "remote $id", seq.toInt(), isActive,
            100, 200, deletedAt, 1, seq)

    private class FakeRemote : InventoryAreaSyncRemoteDataSource {
        val applied = mutableListOf<InventoryAreaRemoteOperation>()
        val pulls = mutableListOf<Triple<String, Long, Int>>()
        val pages = ArrayDeque<List<RemoteInventoryArea>>()
        var applyHandler: suspend (InventoryAreaRemoteOperation) -> InventoryAreaRemoteApplyResult = {
            InventoryAreaRemoteApplyResult.Applied(it.entityId, 1, 1)
        }
        var applyResultHandler: (suspend (InventoryAreaRemoteOperation) -> Result<InventoryAreaRemoteApplyResult>)? = null
        var pullHandler: (suspend (RestaurantId, Long, Int) -> List<RemoteInventoryArea>)? = null

        override suspend fun apply(operation: InventoryAreaRemoteOperation): Result<InventoryAreaRemoteApplyResult> {
            applied += operation
            return applyResultHandler?.invoke(operation) ?: Result.success(applyHandler(operation))
        }

        override suspend fun pull(restaurantId: RestaurantId, afterChangeSeq: Long, limit: Int): Result<List<RemoteInventoryArea>> {
            pulls += Triple(restaurantId.value, afterChangeSeq, limit)
            return Result.success(pullHandler?.invoke(restaurantId, afterChangeSeq, limit) ?: pages.removeFirstOrNull().orEmpty())
        }
    }

    private class QueueIds : IdGenerator {
        val values = ArrayDeque<String>()
        override fun newId(): String = values.removeFirst()
    }
    private object FixedTime : TimeProvider { override fun now(): Instant = Instant.ofEpochMilli(50) }
    private class TestTransportException : Exception()

    private companion object { val RESTAURANT = RestaurantId("restaurant") }
}
