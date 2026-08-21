package com.venkoi.restaurantops.core.backup.internal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.model.BackupSnapshotDto
import com.venkoi.restaurantops.core.backup.platform.RoomBackupSnapshotSource
import io.mockk.mockk
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.PurchaseReceiptEntity
import com.venkoi.restaurantops.core.database.entity.WasteEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreDatabaseApplierIntegrationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var applier: RoomRestoreDatabaseApplier

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        applier = RoomRestoreDatabaseApplier(db, db.backupDao(), db.restoreDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun existing_data_is_fully_replaced() = runBlocking {
        // 1. Seed initial data (State A)
        db.restaurantDao().insert(RestaurantEntity("r1", "Old", "USD", "en-US", 100, 100, null))
        db.inventoryAreaDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity("a1", "r1", "Area 1", "area 1", 0, true, 0, 0, null))
        db.unitDao().insertSeedUnits(listOf(com.venkoi.restaurantops.core.database.entity.UnitEntity("u1", "U", "u", "MASS", java.math.BigDecimal.ONE, true, 0)))
        db.ingredientDao().insert(com.venkoi.restaurantops.core.database.entity.IngredientEntity("i1", "r1", "Ing 1", "ing 1", null, "u1", "a1", null, null, null, true, 0, 0, null))
        
        // 2. Prepare backup snapshot (State B - Materially different)
        val snapshotB = createMinimalSnapshot("r2", "New").copy(
            inventoryAreas = listOf(com.venkoi.restaurantops.core.backup.model.InventoryAreaBackupDto("a2", "r2", "Area 2", "area 2", 0, true, 0, 0, null))
        )
        
        // 3. Replace
        val manifest = com.venkoi.restaurantops.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.restaurantops",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = "r2",
            restaurantName = "New",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments")
        )
        applier.replaceWithBackup(snapshotB, manifest)
        
        // 4. Verify exact preservation and removal
        val backupDao = db.backupDao()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
        val restoredSnapshot = snapshotSource.loadSnapshot("r2").dto
        
        assertThat(restoredSnapshot).isEqualTo(snapshotB)
        
        // State A records must be GONE
        assertThat(db.restaurantDao().getById("r1")).isNull()
        assertThat(db.inventoryAreaDao().getById("a1")).isNull()
        assertThat(db.ingredientDao().getById("i1")).isNull()
    }

    @Test
    fun incoming_no_attachment_backup_produces_null_attachment_paths() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity("r1", "R", "USD", "en-US", 0, 0, null))
        
        val snapshot = createMinimalSnapshot("r1", "R").copy(
            purchaseReceipts = listOf(com.venkoi.restaurantops.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "r1", null, null, 0, "DRAFT", null, null, null, 0, 0, null, null
            ))
        )
        
        val manifest = com.venkoi.restaurantops.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.restaurantops",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = "r1",
            restaurantName = "R",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments")
        )
        applier.replaceWithBackup(snapshot, manifest)
        
        val entity = db.purchaseDao().getReceiptById("p1")
        assertThat(entity?.attachmentPath).isNull()
    }

    @Test
    fun current_attachment_references_are_detected() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity("r1", "R", "USD", "en-US", 0, 0, null))
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity(
            "p1", "r1", null, null, 0, "DRAFT", null, "some/path", null, 0, 0, null, null
        ))
        
        assertThat(applier.hasExistingAttachmentReferences()).isTrue()
    }

    @Test
    fun replaceWithBackup_atomicity_on_failure() = runBlocking {
        // 1. Seed valid existing database state A
        db.restaurantDao().insert(RestaurantEntity("r1", "State A", "USD", "en-US", 100, 100, null))
        db.inventoryAreaDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity("a1", "r1", "Area 1", "area 1", 0, true, 0, 0, null))
        db.unitDao().insertSeedUnits(listOf(com.venkoi.restaurantops.core.database.entity.UnitEntity("u1", "U", "u", "MASS", java.math.BigDecimal.ONE, true, 0)))
        db.ingredientDao().insert(com.venkoi.restaurantops.core.database.entity.IngredientEntity("i1", "r1", "Ing 1", "ing 1", null, "u1", "a1", null, null, null, true, 0, 0, null))
        db.inventoryProjectionDao().upsert(com.venkoi.restaurantops.core.database.entity.InventoryBalanceProjectionEntity("r1", "i1", "a1", "100", 1000L))
        
        val backupDao = db.backupDao()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
        val stateASnapshot = snapshotSource.loadSnapshot("r1").dto

        // 2. Construct replacement snapshot B that fails during insertion (FK violation)
        val snapshotB = createMinimalSnapshot("r2", "State B").copy(
            purchaseLines = listOf(com.venkoi.restaurantops.core.backup.model.PurchaseLineBackupDto(
                id = "pl1",
                purchaseReceiptId = "p1", // Missing Receipt (FK violation)
                ingredientId = "i1",
                areaId = "a1",
                ingredientUnitOptionId = "o1",
                quantityEntered = "1",
                quantityBase = "1",
                lineTotal = "10",
                unitCostBase = "10",
                notes = null,
                createdAt = 200,
                updatedAt = 200
            ))
        )

        // 3. Invoke replaceWithBackup(B)
        val manifestB = com.venkoi.restaurantops.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.restaurantops",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = "r2",
            restaurantName = "State B",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments")
        )
        val result = try {
            applier.replaceWithBackup(snapshotB, manifestB)
            null
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            e
        }
        
        // 4. Require the operation to fail with specific exception
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(android.database.sqlite.SQLiteConstraintException::class.java)

        // 5. Reload the database and require exact equality with state A
        val currentSnapshot = snapshotSource.loadSnapshot("r1").dto
        assertThat(currentSnapshot).isEqualTo(stateASnapshot)
        
        // Explicitly check State A survivors
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("State A")
        assertThat(db.inventoryAreaDao().getById("a1")).isNotNull()
        assertThat(db.ingredientDao().getById("i1")).isNotNull()
        assertThat(db.inventoryProjectionDao().getBalance("i1", "a1")?.quantityBase).isEqualTo("100")
        
        // Explicitly check State B rejection
        assertThat(db.restaurantDao().getById("r2")).isNull()
        // Ensure no rows from State B leaked through (e.g. the restaurant r2 which was inserted first)
        assertThat(db.backupDao().createSnapshot("r2").restaurants).isEmpty()
    }

    private fun createMinimalSnapshot(id: String, name: String) = BackupSnapshotDto(
        restaurants = listOf(com.venkoi.restaurantops.core.backup.model.RestaurantBackupDto(id, name, "USD", "en-US", 0, 0, null)),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )
}
