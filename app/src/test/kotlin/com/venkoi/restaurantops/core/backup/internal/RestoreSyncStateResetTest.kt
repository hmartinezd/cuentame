package com.venkoi.restaurantops.core.backup.internal

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.model.BackupSnapshotDto
import com.venkoi.restaurantops.core.backup.model.InventoryAreaBackupDto
import com.venkoi.restaurantops.core.backup.model.RestaurantBackupDto
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.SyncCursorEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity
import com.venkoi.restaurantops.core.database.sync.INVENTORY_AREA_ENTITY_TYPE
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncOutboxWriter
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncPreparation
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import kotlinx.coroutines.test.runTest
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
class RestoreSyncStateResetTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var applier: RoomRestoreDatabaseApplier

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        applier = RoomRestoreDatabaseApplier(db, db.backupDao(), db.restoreDao())
    }

    @After fun tearDown() = db.close()

    @Test fun `successful restore clears device sync state and explicit preparation is idempotent`() = runTest {
        seedOldBusinessAndSyncState()

        applier.replaceWithBackup(snapshot("restored", "restored-area"), manifest("restored"))

        assertThat(db.restaurantDao().getById("old")).isNull()
        assertThat(db.restaurantDao().getById("restored")).isNotNull()
        assertThat(db.inventoryAreaDao().getById("restored-area")).isNotNull()
        assertSyncStateIsEmpty("old-area")

        val writer = InventoryAreaSyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), UuidIds, FixedTime,
            Json { encodeDefaults = true }
        )
        val preparation = InventoryAreaSyncPreparation(db, writer)
        preparation.prepareUnsyncedInventoryAreas("restored")
        preparation.prepareUnsyncedInventoryAreas("restored")

        val pending = db.syncOutboxDao()
            .getForEntity(INVENTORY_AREA_ENTITY_TYPE, "restored-area")
        assertThat(pending).hasSize(1)
        assertThat(pending.single().baseServerVersion).isEqualTo(0)
    }

    @Test fun `restore failure rolls back business clearing and sync state clearing together`() = runTest {
        seedOldBusinessAndSyncState()
        val invalid = snapshot("new", "new-area").copy(
            restaurants = listOf(
                RestaurantBackupDto("new", "New", "USD", "en-US", 1, 1, null),
                RestaurantBackupDto("new", "Duplicate", "USD", "en-US", 1, 1, null)
            )
        )

        val result = runCatching { applier.replaceWithBackup(invalid, manifest("new")) }

        assertThat(result.isFailure).isTrue()
        assertThat(db.restaurantDao().getById("old")).isNotNull()
        assertThat(db.inventoryAreaDao().getById("old-area")).isNotNull()
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, "old-area")).isNotNull()
        assertThat(db.syncCursorDao().get("old", INVENTORY_AREA_ENTITY_TYPE)?.changeSeq).isEqualTo(7)
        assertThat(db.syncOutboxDao().getForEntity(INVENTORY_AREA_ENTITY_TYPE, "old-area")).hasSize(1)
        assertThat(db.restaurantDao().getById("new")).isNull()
    }

    private suspend fun seedOldBusinessAndSyncState() {
        db.restaurantDao().insert(RestaurantEntity("old", "Old", "USD", "en-US", 1, 1, null))
        db.inventoryAreaDao().upsert(
            InventoryAreaEntity("old-area", "old", "Old Area", "old area", 0, true, 1, 1, null)
        )
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(INVENTORY_AREA_ENTITY_TYPE, "old-area", "old", 3, 7)
        )
        db.syncCursorDao().upsert(SyncCursorEntity("old", INVENTORY_AREA_ENTITY_TYPE, 7))
        db.syncOutboxDao().insert(
            SyncOutboxEntity(
                operationId = UUID.randomUUID().toString(), restaurantId = "old",
                entityType = INVENTORY_AREA_ENTITY_TYPE, entityId = "old-area",
                baseServerVersion = 3, payloadJson = "{}", createdAt = 1
            )
        )
    }

    private suspend fun assertSyncStateIsEmpty(oldEntityId: String) {
        assertThat(db.syncEntityMetadataDao().get(INVENTORY_AREA_ENTITY_TYPE, oldEntityId)).isNull()
        assertThat(db.syncCursorDao().get("old", INVENTORY_AREA_ENTITY_TYPE)).isNull()
        assertThat(db.syncOutboxDao().getPending("old", INVENTORY_AREA_ENTITY_TYPE, 10)).isEmpty()
        assertThat(db.syncOutboxDao().getPending("restored", INVENTORY_AREA_ENTITY_TYPE, 10)).isEmpty()
    }

    private fun snapshot(restaurantId: String, areaId: String) = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restaurantId, "Restored", "USD", "en-US", 2, 2, null)),
        inventoryAreas = listOf(InventoryAreaBackupDto(areaId, restaurantId, "Kitchen", "kitchen", 0, true, 2, 2, null)),
        ingredientCategories = emptyList(), units = emptyList(), ingredients = emptyList(),
        ingredientUnitOptions = emptyList(), suppliers = emptyList(), purchaseReceipts = emptyList(),
        purchaseLines = emptyList(), stockCounts = emptyList(), stockCountAreas = emptyList(),
        stockCountLines = emptyList(), wasteEvents = emptyList(), inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(), ingredientCostProjections = emptyList()
    )

    private fun manifest(restaurantId: String) = BackupManifest(
        backupFormatVersion = 1, createdAtUtc = "2026-08-21T00:00:00Z",
        applicationId = "com.venkoi.restaurantops", appVersionName = "1", appVersionCode = 1,
        databaseSchemaVersion = 17, restaurantId = restaurantId, restaurantName = "Restored",
        localeTag = "en-US", currencyCode = "USD", tableMetadata = emptyMap(),
        attachments = emptyList(), includedSections = listOf("data", "preferences", "attachments")
    )

    private object UuidIds : IdGenerator {
        override fun newId(): String = UUID.randomUUID().toString()
    }

    private object FixedTime : TimeProvider {
        override fun now(): Instant = Instant.ofEpochMilli(10)
    }
}
