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
import com.miara.cuentame.core.domain.repository.SetupCategoryInput
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.domain.validation.ValidationError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLocalSetupRepositoryTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomLocalSetupRepository
    private val validator = LocalSetupValidator()

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
            SystemTimeProvider(),
            validator
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
            categories = listOf(SetupCategoryInput("Meat", 0))
        )

        val result = repository.completeSetup(command)

        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(repository.isSetupComplete()).isTrue()
        assertThat(db.restaurantDao().getRestaurant()?.name).isEqualTo("Test Rest")
    }

    @Test
    fun completeSetup_invalidCommand_rollsBack() = runBlocking {
        val command = CompleteLocalSetupCommand(
            restaurantName = "", // Invalid
            currencyCode = "USD",
            localeTag = "en-US",
            areas = listOf(SetupAreaInput("Kitchen", 0)),
            categories = emptyList()
        )

        val result = repository.completeSetup(command)
        assertThat(result).isInstanceOf(LocalSetupResult.Failure::class.java)
        val failure = result as LocalSetupResult.Failure
        assertThat(failure.error).isEqualTo(ValidationError.InvalidName)
        
        assertThat(db.restaurantDao().getRestaurant()).isNull()
    }

    @Test
    fun completeSetup_recoveryFromIncomplete() = runBlocking {
        // Create restaurant only
        val restaurantId = "rest_1"
        db.restaurantDao().insert(com.miara.cuentame.core.database.entity.RestaurantEntity(
            id = restaurantId, name = "Old Name", currencyCode = "EUR", localeTag = "es-ES",
            createdAt = 0, updatedAt = 0, deletedAt = null
        ))
        
        assertThat(repository.isSetupComplete()).isFalse()

        val command = CompleteLocalSetupCommand(
            restaurantName = "New Name",
            currencyCode = "USD",
            localeTag = "en-US",
            areas = listOf(SetupAreaInput("Kitchen", 0)),
            categories = emptyList()
        )

        val result = repository.completeSetup(command)
        assertThat(result).isEqualTo(LocalSetupResult.Success)
        
        val restaurant = db.restaurantDao().getRestaurant()
        assertThat(restaurant?.name).isEqualTo("New Name")
        assertThat(restaurant?.id).isEqualTo(restaurantId)
        assertThat(repository.isSetupComplete()).isTrue()
    }

    @Test
    fun isSetupComplete_returnsFalseIfNoRestaurant() = runBlocking {
        assertThat(repository.isSetupComplete()).isFalse()
    }

    @Test
    fun completeSetup_alreadyComplete_returnsAlreadyCompleted() = runBlocking {
        val command = CompleteLocalSetupCommand(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            areas = listOf(SetupAreaInput("Kitchen", 0)),
            categories = emptyList()
        )

        repository.completeSetup(command)
        
        val result = repository.completeSetup(command)
        assertThat(result).isEqualTo(LocalSetupResult.AlreadyCompleted)
    }
}
