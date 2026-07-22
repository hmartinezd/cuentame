package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.UuidIdGenerator
import com.miara.cuentame.core.common.time.SystemTimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.SetupAreaInput
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLocalSetupRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomLocalSetupRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLocalSetupRepository(
            db,
            db.restaurantDao(),
            db.inventoryAreaDao(),
            db.ingredientCategoryDao(),
            UuidIdGenerator(),
            SystemTimeProvider()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun completeSetup_insertsRecordsTransactionally() = runBlocking {
        val command = CompleteLocalSetupCommand(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            areas = listOf(SetupAreaInput("Kitchen", 0)),
            categories = emptyList()
        )

        val result = repository.completeSetup(command)

        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(repository.isSetupComplete()).isTrue()
        assertThat(db.restaurantDao().getRestaurant()?.name).isEqualTo("Test Rest")
    }

    @Test
    fun isSetupComplete_returnsFalseIfNoRestaurant() = runBlocking {
        assertThat(repository.isSetupComplete()).isFalse()
    }
}
