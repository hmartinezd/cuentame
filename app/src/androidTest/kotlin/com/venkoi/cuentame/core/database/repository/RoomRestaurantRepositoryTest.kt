package com.venkoi.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomRestaurantRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomRestaurantRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        repository = RoomRestaurantRepository(db.restaurantDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun save_newRestaurant_inserts() = runBlocking {
        val rest = Restaurant(RestaurantId("r1"), "Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        repository.save(rest)
        
        val loaded = repository.getRestaurant()
        assertThat(loaded?.name).isEqualTo("Rest")
    }

    @Test
    fun save_existingRestaurant_updates() = runBlocking {
        val rest1 = Restaurant(RestaurantId("r1"), "Rest 1", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        repository.save(rest1)
        
        val rest2 = Restaurant(RestaurantId("r1"), "Rest 2", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        repository.save(rest2)
        
        val loaded = repository.getRestaurant()
        assertThat(loaded?.name).isEqualTo("Rest 2")
    }
}
