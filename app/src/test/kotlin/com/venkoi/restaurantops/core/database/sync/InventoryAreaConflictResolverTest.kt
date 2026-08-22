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
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InventoryAreaConflictResolverTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var remote: FakeRemote
    private lateinit var resolver: InventoryAreaConflictResolver
    private lateinit var syncService: InventoryAreaSyncService
    private val json = Json { encodeDefaults = true }
    private val ids = QueueIds()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        remote = FakeRemote()
        val writer = InventoryAreaSyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), ids, FixedTime, json
        )
        resolver = InventoryAreaConflictResolver(db, remote, writer)
        syncService = InventoryAreaSyncService(db, InventoryAreaSyncPreparation(db, writer), remote)
    }

    @After fun tearDown() = db.close()

    @Test fun `keep local collapses branch into fresh UUID snapshot with fresh cloud base and preserves cursor`() = runTest {
        seed("Local New")
        insertOperation(1, "conflict-x", "Old")
        insertOperation(2, "later-y", "Later")
        db.syncCursorDao().upsert(SyncCursorEntity(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, 40))
        remote.current = remoteArea("Cloud New", version = 5, seq = 42)
        ids.values += NEW_UUID

        val result = resolver.resolveKeepLocal(CONFLICT)

        assertThat(result).isEqualTo(
            InventoryAreaConflictResolutionResult.KeepLocalPrepared(AREA_ID, NEW_UUID, 5)
        )
        assertThat(remote.gets).containsExactly(RESTAURANT.value to AREA_ID)
        val operation = db.syncOutboxDao().getAll().single()
        assertThat(operation.operationId).isEqualTo(NEW_UUID)
        assertThat(UUID.fromString(operation.operationId).version()).isEqualTo(4)
        assertThat(operation.baseServerVersion).isEqualTo(5)
        assertThat(json.decodeFromString<InventoryAreaSyncPayload>(operation.payloadJson))
            .isEqualTo(payload("Local New"))
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, AREA_ID))
            .isEqualTo(SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, AREA_ID, RESTAURANT.value, 5, 42))
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(40)
    }

    @Test fun `keep local replacement syncs with new identity and selected base`() = runTest {
        seed("Current Local"); insertOperation(1, "conflict-x", "Old")
        remote.current = remoteArea("Cloud", version = 5, seq = 42)
        ids.values += NEW_UUID
        assertThat(resolver.resolveKeepLocal(CONFLICT)).isInstanceOf(
            InventoryAreaConflictResolutionResult.KeepLocalPrepared::class.java
        )
        remote.applyHandler = { InventoryAreaRemoteApplyResult.Applied(it.entityId, 6, 43) }

        assertThat(syncService.sync(RESTAURANT)).isEqualTo(InventoryAreaSyncResult.Success(1, 0, 0))
        val sent = remote.applied.single()
        assertThat(sent.operationId).isEqualTo(NEW_UUID)
        assertThat(sent.baseServerVersion).isEqualTo(5)
        assertThat(json.decodeFromString<InventoryAreaSyncPayload>(sent.payloadJson)).isEqualTo(payload("Current Local"))
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, AREA_ID)?.serverVersion).isEqualTo(6)
    }

    @Test fun `remote race after keep local returns normal conflict and preserves replacement`() = runTest {
        seed("Current Local"); insertOperation(1, "conflict-x", "Old")
        remote.current = remoteArea("Cloud", version = 5, seq = 42)
        ids.values += NEW_UUID
        resolver.resolveKeepLocal(CONFLICT)
        remote.applyHandler = { InventoryAreaRemoteApplyResult.Conflict(it.entityId, 6, 43) }

        assertThat(syncService.sync(RESTAURANT)).isEqualTo(
            InventoryAreaSyncResult.Conflict(AREA_ID, NEW_UUID, 6, 43)
        )
        assertThat(db.syncOutboxDao().getAll().single().operationId).isEqualTo(NEW_UUID)
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, AREA_ID)?.serverVersion).isEqualTo(5)
    }

    @Test fun `use cloud atomically replaces business metadata and branch without advancing cursor`() = runTest {
        seed("Local New"); insertOperation(1, "conflict-x", "Old"); insertOperation(2, "later-y", "Later")
        db.syncCursorDao().upsert(SyncCursorEntity(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, 40))
        remote.current = remoteArea("Cloud New", version = 5, seq = 42)

        assertThat(resolver.resolveUseCloud(CONFLICT)).isEqualTo(
            InventoryAreaConflictResolutionResult.CloudAccepted(AREA_ID, 5, 42)
        )
        assertThat(db.inventoryAreaDao().getById(AREA_ID)).isEqualTo(remote.current!!.toExpectedEntity())
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, AREA_ID)?.changeSeq).isEqualTo(42)
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(40)
    }

    @Test fun `use cloud applies remote tombstone without outbox`() = runTest {
        seed("Local"); insertOperation(1, "conflict-x", "Local")
        remote.current = remoteArea("Archived", version = 7, seq = 50, isActive = false, deletedAt = 900)

        assertThat(resolver.resolveUseCloud(CONFLICT)).isEqualTo(
            InventoryAreaConflictResolutionResult.CloudAccepted(AREA_ID, 7, 50)
        )
        assertThat(db.inventoryAreaDao().getById(AREA_ID)?.isActive).isFalse()
        assertThat(db.inventoryAreaDao().getById(AREA_ID)?.deletedAt).isEqualTo(900)
        assertThat(db.syncOutboxDao().getAll()).isEmpty()
    }

    @Test fun `keep local preserves local tombstone in replacement payload`() = runTest {
        seed("Archived Local", isActive = false, deletedAt = 800); insertOperation(1, "conflict-x", "Old")
        remote.current = remoteArea("Cloud", version = 5, seq = 42)
        ids.values += NEW_UUID

        resolver.resolveKeepLocal(CONFLICT)

        val replacement = json.decodeFromString<InventoryAreaSyncPayload>(db.syncOutboxDao().getAll().single().payloadJson)
        assertThat(replacement.isActive).isFalse()
        assertThat(replacement.deletedAt).isEqualTo(800)
    }

    @Test fun `stale conflict choices make no local changes and do not delete unrelated work`() = runTest {
        seed("Local"); insertOperation(2, "other", "Later", entityId = "other-area")
        remote.current = remoteArea("Cloud", version = 5, seq = 42)
        val beforeArea = db.inventoryAreaDao().getById(AREA_ID)
        val beforeMetadata = db.syncEntityMetadataDao().getAll()

        assertThat(resolver.resolveKeepLocal(CONFLICT)).isEqualTo(InventoryAreaConflictResolutionResult.StaleConflict)
        assertThat(resolver.resolveUseCloud(CONFLICT)).isEqualTo(InventoryAreaConflictResolutionResult.StaleConflict)
        assertThat(db.inventoryAreaDao().getById(AREA_ID)).isEqualTo(beforeArea)
        assertThat(db.syncEntityMetadataDao().getAll()).isEqualTo(beforeMetadata)
        assertThat(db.syncOutboxDao().getAll().single().operationId).isEqualTo("other")
    }

    @Test fun `remote failure for both choices preserves exact complete local state`() = runTest {
        seed("Local"); insertOperation(1, "conflict-x", "Old"); insertOperation(2, "later-y", "Later")
        db.syncCursorDao().upsert(SyncCursorEntity(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, 40))
        remote.currentResult = Result.failure(TestTransportException())
        val beforeArea = db.inventoryAreaDao().getById(AREA_ID)
        val beforeMetadata = db.syncEntityMetadataDao().getAll()
        val beforeOutbox = db.syncOutboxDao().getAll()

        assertThat(resolver.resolveKeepLocal(CONFLICT)).isEqualTo(InventoryAreaConflictResolutionResult.RemoteFailure)
        assertThat(resolver.resolveUseCloud(CONFLICT)).isEqualTo(InventoryAreaConflictResolutionResult.RemoteFailure)
        assertThat(db.inventoryAreaDao().getById(AREA_ID)).isEqualTo(beforeArea)
        assertThat(db.syncEntityMetadataDao().getAll()).isEqualTo(beforeMetadata)
        assertThat(db.syncOutboxDao().getAll()).isEqualTo(beforeOutbox)
        assertThat(db.syncCursorDao().get(RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(40)
    }

    @Test fun `missing malformed or mismatched remote state is protocol failure without mutation`() = runTest {
        seed("Local"); insertOperation(1, "conflict-x", "Old")
        val beforeArea = db.inventoryAreaDao().getById(AREA_ID)
        val beforeMetadata = db.syncEntityMetadataDao().getAll()
        val beforeOutbox = db.syncOutboxDao().getAll()
        val invalidRows = listOf<RemoteInventoryArea?>(
            null,
            remoteArea("Cloud", 5, 42).copy(restaurantId = "wrong"),
            remoteArea("Cloud", 5, 42).copy(id = "wrong"),
            remoteArea("Cloud", 0, 42),
            remoteArea("Cloud", 5, 0)
        )

        invalidRows.forEach { row ->
            remote.currentResult = Result.success(row)
            assertThat(resolver.resolveUseCloud(CONFLICT))
                .isEqualTo(InventoryAreaConflictResolutionResult.ProtocolFailure)
            assertThat(db.inventoryAreaDao().getById(AREA_ID)).isEqualTo(beforeArea)
            assertThat(db.syncEntityMetadataDao().getAll()).isEqualTo(beforeMetadata)
            assertThat(db.syncOutboxDao().getAll()).isEqualTo(beforeOutbox)
        }
    }

    @Test fun `resolution database failure rolls back outbox business and metadata atomically`() = runTest {
        seed("Local"); insertOperation(1, "conflict-x", "Old"); insertOperation(2, "later-y", "Later")
        remote.current = remoteArea("Cloud", version = 5, seq = 42)
        val beforeArea = db.inventoryAreaDao().getById(AREA_ID)
        val beforeMetadata = db.syncEntityMetadataDao().getAll()
        val beforeOutbox = db.syncOutboxDao().getAll()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_resolution_metadata BEFORE INSERT ON sync_entity_metadata
            WHEN NEW.entityId = '$AREA_ID' AND NEW.serverVersion = 5
            BEGIN SELECT RAISE(ABORT, 'forced'); END
        """.trimIndent())

        assertThat(resolver.resolveUseCloud(CONFLICT))
            .isEqualTo(InventoryAreaConflictResolutionResult.ProtocolFailure)
        assertThat(db.inventoryAreaDao().getById(AREA_ID)).isEqualTo(beforeArea)
        assertThat(db.syncEntityMetadataDao().getAll()).isEqualTo(beforeMetadata)
        assertThat(db.syncOutboxDao().getAll()).isEqualTo(beforeOutbox)
    }

    private suspend fun seed(name: String, isActive: Boolean = true, deletedAt: Long? = null) {
        db.restaurantDao().insert(RestaurantEntity(RESTAURANT.value, "Restaurant", "USD", "en-US", 1, 1, null))
        db.inventoryAreaDao().upsert(area(name, isActive, deletedAt))
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, AREA_ID, RESTAURANT.value, 4, 35)
        )
    }

    private suspend fun insertOperation(
        sequence: Long,
        operationId: String,
        name: String,
        entityId: String = AREA_ID
    ) {
        db.syncOutboxDao().insert(SyncOutboxEntity(
            sequence, operationId, RESTAURANT.value, INVENTORY_AREA_ENTITY_TYPE, entityId, 4,
            json.encodeToString(payload(name).copy(id = entityId)), 50
        ))
    }

    private fun area(name: String, isActive: Boolean, deletedAt: Long?) = InventoryAreaEntity(
        AREA_ID, RESTAURANT.value, name, name.lowercase(), 0, isActive, 100, 200, deletedAt
    )

    private fun payload(name: String) = InventoryAreaSyncPayload(
        AREA_ID, RESTAURANT.value, name, name.lowercase(), 0, true, 100, 200, null
    )

    private fun remoteArea(
        name: String,
        version: Long,
        seq: Long,
        isActive: Boolean = true,
        deletedAt: Long? = null
    ) = RemoteInventoryArea(
        AREA_ID, RESTAURANT.value, name, name.lowercase(), 0, isActive,
        100, 200, deletedAt, version, seq
    )

    private fun RemoteInventoryArea.toExpectedEntity() = InventoryAreaEntity(
        id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt, deletedAt
    )

    private class FakeRemote : InventoryAreaSyncRemoteDataSource {
        val gets = mutableListOf<Pair<String, String>>()
        val applied = mutableListOf<InventoryAreaRemoteOperation>()
        var current: RemoteInventoryArea? = null
        var currentResult: Result<RemoteInventoryArea?>? = null
        var applyHandler: suspend (InventoryAreaRemoteOperation) -> InventoryAreaRemoteApplyResult = {
            InventoryAreaRemoteApplyResult.Applied(it.entityId, 1, 1)
        }

        override suspend fun apply(operation: InventoryAreaRemoteOperation): Result<InventoryAreaRemoteApplyResult> {
            applied += operation
            return Result.success(applyHandler(operation))
        }

        override suspend fun pull(
            restaurantId: RestaurantId,
            afterChangeSeq: Long,
            limit: Int
        ): Result<List<RemoteInventoryArea>> = Result.success(emptyList())

        override suspend fun getCurrent(
            restaurantId: RestaurantId,
            entityId: String
        ): Result<RemoteInventoryArea?> {
            gets += restaurantId.value to entityId
            return currentResult ?: Result.success(current)
        }
    }

    private class QueueIds : IdGenerator {
        val values = ArrayDeque<String>()
        override fun newId(): String = values.removeFirst()
    }

    private object FixedTime : TimeProvider { override fun now(): Instant = Instant.ofEpochMilli(50) }
    private class TestTransportException : Exception()

    private companion object {
        val RESTAURANT = RestaurantId("restaurant")
        const val AREA_ID = "area"
        const val NEW_UUID = "11111111-1111-4111-8111-111111111111"
        val CONFLICT = InventoryAreaConflictRef(RESTAURANT, AREA_ID, "conflict-x")
    }
}
