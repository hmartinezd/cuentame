package com.miara.cuentame.core.backup.internal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.BackupMapper
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
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
        // 1. Seed initial data
        db.restaurantDao().insert(RestaurantEntity("r1", "Old", "USD", "en-US", 0, 0, null))
        
        // 2. Prepare backup snapshot (Empty but with new restaurant)
        val snapshot = createMinimalSnapshot("r2", "New")
        
        // 3. Replace
        applier.replaceWithBackup(snapshot)
        
        // 4. Verify
        val current = db.restaurantDao().getById("r2")
        assertThat(current?.name).isEqualTo("New")
        assertThat(db.restaurantDao().getById("r1")).isNull()
    }

    @Test
    fun incoming_no_attachment_backup_produces_null_attachment_paths() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity("r1", "R", "USD", "en-US", 0, 0, null))
        
        val snapshot = createMinimalSnapshot("r1", "R").copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "r1", null, null, 0, "DRAFT", null, null, 0, 0, null, null
            ))
        )
        
        applier.replaceWithBackup(snapshot)
        
        val entity = db.purchaseDao().getReceiptById("p1")
        assertThat(entity?.attachmentPath).isNull()
    }

    @Test
    fun current_attachment_references_are_detected() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity("r1", "R", "USD", "en-US", 0, 0, null))
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity(
            "p1", "r1", null, null, 0, "DRAFT", null, "some/path", 0, 0, null, null
        ))
        
        assertThat(applier.hasExistingAttachmentReferences()).isTrue()
    }

    private fun createMinimalSnapshot(id: String, name: String) = BackupSnapshotDto(
        restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto(id, name, "USD", "en-US", 0, 0, null)),
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
