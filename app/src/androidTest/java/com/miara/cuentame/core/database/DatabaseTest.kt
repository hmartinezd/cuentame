package com.miara.cuentame.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.seed.UnitSeeds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: RestaurantInventoryDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadRestaurant() = runBlocking {
        val restaurant = TestFactories.createRestaurant()
        db.restaurantDao().insert(restaurant)
        val read = db.restaurantDao().getRestaurant()
        assertThat(read).isEqualTo(restaurant)
    }

    @Test
    fun seedUnitsExist() = runBlocking {
        db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
        val count = db.unitDao().countSeededUnits()
        assertThat(count).isEqualTo(UnitSeeds.ALL_UNITS.size)
    }

    @Test
    fun foreignKeyConstraints() = runBlocking {
        val area = TestFactories.createArea(restaurantId = "missing_rest")
        try {
            db.inventoryAreaDao().upsert(area)
            // Should fail
            assertThat(true).isFalse()
        } catch (e: Exception) {
            assertThat(e).isNotNull()
        }
    }
}
