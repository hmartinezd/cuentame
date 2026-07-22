package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomInventoryAreaRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomInventoryAreaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomInventoryAreaRepository(db, db.inventoryAreaDao())
        
        runBlocking {
            db.restaurantDao().insert(RestaurantEntity("rest_1", "Test", "USD", "en-US", 0, 0, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun archiveFinalArea_throwsError() = runBlocking {
        val area = InventoryAreaEntity("area_1", "rest_1", "Kitchen", "kitchen", 0, true, 0, 0, null)
        db.inventoryAreaDao().upsert(area)
        
        try {
            repository.archive(InventoryAreaId("area_1"), Instant.now())
            assertThat(true).isFalse()
        } catch (e: ValidationError.FinalAreaCannotBeArchived) {
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun archiveFinalArea_scopedByRestaurant() = runBlocking {
        // rest_1 has one area (area_1)
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area_1", "rest_1", "K1", "k1", 0, true, 0, 0, null))
        
        // rest_2 has two areas (area_2, area_3)
        db.restaurantDao().insert(RestaurantEntity("rest_2", "R2", "USD", "en-US", 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area_2", "rest_2", "K2", "k2", 0, true, 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area_3", "rest_2", "K3", "k3", 1, true, 0, 0, null))
        
        // area_2 should be archivable because rest_2 has another active area
        repository.archive(InventoryAreaId("area_2"), Instant.now())
        
        // area_1 should NOT be archivable
        try {
            repository.archive(InventoryAreaId("area_1"), Instant.now())
            assertThat(true).isFalse()
        } catch (e: ValidationError.FinalAreaCannotBeArchived) {
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun reorder_updatesSortOrderContiguously() = runBlocking {
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a1", "rest_1", "A", "a", 0, true, 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a2", "rest_1", "B", "b", 1, true, 0, 0, null))
        
        repository.reorder(listOf(InventoryAreaId("a2"), InventoryAreaId("a1")))
        
        val a1 = db.inventoryAreaDao().getById("a1")
        val a2 = db.inventoryAreaDao().getById("a2")
        
        assertThat(a1?.sortOrder).isEqualTo(1)
        assertThat(a2?.sortOrder).isEqualTo(0)
    }

    @Test
    fun reorder_subset_throwsError() = runBlocking {
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a1", "rest_1", "A", "a", 0, true, 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a2", "rest_1", "B", "b", 1, true, 0, 0, null))
        
        try {
            repository.reorder(listOf(InventoryAreaId("a1")))
            assertThat(true).isFalse()
        } catch (e: ValidationError.InvalidSetupState) {
            assertThat(e).isNotNull()
        }
    }

    @Test
    fun reorder_otherRestaurant_throwsError() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity("rest_2", "Other", "USD", "en-US", 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a1", "rest_1", "A", "a", 0, true, 0, 0, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a2", "rest_2", "B", "b", 0, true, 0, 0, null))
        
        try {
            // Trying to reorder across restaurants
            repository.reorder(listOf(InventoryAreaId("a1"), InventoryAreaId("a2")))
            assertThat(true).isFalse()
        } catch (e: ValidationError.InvalidSetupState) {
            assertThat(e).isNotNull()
        }
    }
}
