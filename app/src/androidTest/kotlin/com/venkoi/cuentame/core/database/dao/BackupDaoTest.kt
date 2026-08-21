package com.venkoi.cuentame.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        dao = db.backupDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createSnapshot_includesAllTables() = runBlocking {
        // Seed some data
        db.restaurantDao().insert(RestaurantEntity("rest-1", "Test Restaurant", "USD", "en", 0L, 0L, null))
        
        val snapshot = dao.createSnapshot("rest-1")
        
        assertThat(snapshot.restaurants).hasSize(1)
        assertThat(snapshot.restaurants[0].name).isEqualTo("Test Restaurant")
        
        // Projections should be empty but exist in snapshot
        assertThat(snapshot.inventoryBalanceProjections).isEmpty()
    }
}
