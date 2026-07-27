package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class BackupIsolationTest {

    private lateinit var db: RestaurantInventoryDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createSnapshot_isolatesDataByRestaurant() = runBlocking {
        // Rest 1
        db.restaurantDao().insert(RestaurantEntity("rest-1", "Rest 1", "USD", "en", 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "Mass", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("ing-1", "rest-1", "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0L, 0L, null))

        // Rest 2
        db.restaurantDao().insert(RestaurantEntity("rest-2", "Rest 2", "USD", "en", 0L, 0L, null))
        db.ingredientDao().insert(IngredientEntity("ing-2", "rest-2", "Ing 2", "ing 2", null, "u1", null, null, null, null, true, 0L, 0L, null))

        // Snapshot for Rest 1
        val snapshot1 = db.backupDao().createSnapshot("rest-1")
        assertThat(snapshot1.restaurants).hasSize(1)
        assertThat(snapshot1.restaurants[0].id).isEqualTo("rest-1")
        assertThat(snapshot1.ingredients).hasSize(1)
        assertThat(snapshot1.ingredients[0].id).isEqualTo("ing-1")

        // Snapshot for Rest 2
        val snapshot2 = db.backupDao().createSnapshot("rest-2")
        assertThat(snapshot2.restaurants).hasSize(1)
        assertThat(snapshot2.restaurants[0].id).isEqualTo("rest-2")
        assertThat(snapshot2.ingredients).hasSize(1)
        assertThat(snapshot2.ingredients[0].id).isEqualTo("ing-2")
    }
}
