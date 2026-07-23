package com.miara.cuentame.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.seed.SystemUnitSeeder
import com.miara.cuentame.core.database.seed.UnitSeeds
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeedingTest {

    @Test
    fun unitsAreSeededSynchronouslyOnCreate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // We simulate the real DatabaseModule callback logic here
        val db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    SystemUnitSeeder.seed(db)
                }
            })
            .allowMainThreadQueries()
            .build()

        val count = db.unitDao().countSeededUnits()
        assertThat(count).isEqualTo(UnitSeeds.ALL_UNITS.size)
        db.close()
    }
}
