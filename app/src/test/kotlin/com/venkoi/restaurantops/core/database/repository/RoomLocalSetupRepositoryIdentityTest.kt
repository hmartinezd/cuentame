package com.venkoi.restaurantops.core.database.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.domain.repository.CompleteLocalSetupCommand
import com.venkoi.restaurantops.core.domain.repository.LocalSetupResult
import com.venkoi.restaurantops.core.domain.repository.SetupAreaInput
import com.venkoi.restaurantops.core.domain.repository.SetupCategoryInput
import com.venkoi.restaurantops.core.domain.usecase.LocalSetupValidator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomLocalSetupRepositoryIdentityTest {

    private lateinit var database: RestaurantInventoryDatabase
    private lateinit var ids: QueueIdGenerator
    private lateinit var repository: RoomLocalSetupRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        ids = QueueIdGenerator()
        repository = RoomLocalSetupRepository(
            database = database,
            restaurantDao = database.restaurantDao(),
            areaDao = database.inventoryAreaDao(),
            categoryDao = database.ingredientCategoryDao(),
            idGenerator = ids,
            timeProvider = FixedTimeProvider,
            validator = LocalSetupValidator()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `new restaurant uses provided authoritative id exactly`() = runTest {
        ids.enqueue("area-1", "category-1")

        val result = repository.completeSetup(command(RestaurantId("cloud-restaurant")))

        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(database.restaurantDao().getRestaurant()?.id).isEqualTo("cloud-restaurant")
    }

    @Test
    fun `new restaurant without provided id uses generator`() = runTest {
        ids.enqueue("generated-restaurant", "area-1", "category-1")

        val result = repository.completeSetup(command())

        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(database.restaurantDao().getRestaurant()?.id).isEqualTo("generated-restaurant")
    }

    @Test
    fun `incomplete restaurant with matching id continues recovery`() = runTest {
        insertRestaurant("cloud-restaurant", name = "Old Name")
        ids.enqueue("area-1", "category-1")

        val result = repository.completeSetup(command(RestaurantId("cloud-restaurant")))

        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(database.restaurantDao().getRestaurant()?.id).isEqualTo("cloud-restaurant")
        assertThat(database.restaurantDao().getRestaurant()?.name).isEqualTo("Restaurant")
        assertThat(database.inventoryAreaDao().getActiveCount("cloud-restaurant")).isEqualTo(1)
    }

    @Test
    fun `different authoritative id fails without changing existing data or inserting setup rows`() = runTest {
        val original = insertRestaurant("local-restaurant", name = "Local Restaurant")

        val result = repository.completeSetup(command(RestaurantId("different-cloud-restaurant")))

        assertThat(result).isInstanceOf(LocalSetupResult.Failure::class.java)
        assertThat((result as LocalSetupResult.Failure).error)
            .isInstanceOf(LocalRestaurantIdentityMismatchException::class.java)
        assertThat(database.restaurantDao().getRestaurant()).isEqualTo(original)
        assertThat(database.inventoryAreaDao().getActiveCount("local-restaurant")).isEqualTo(0)
        assertThat(database.inventoryAreaDao().getActiveCount("different-cloud-restaurant")).isEqualTo(0)
        assertThat(database.ingredientCategoryDao().getAllCategoriesForRestaurant("local-restaurant"))
            .isEmpty()
        assertThat(database.ingredientCategoryDao().getAllCategoriesForRestaurant("different-cloud-restaurant"))
            .isEmpty()
        assertThat(ids.calls).isEqualTo(0)
    }

    @Test
    fun `completed restaurant with matching id remains already completed`() = runTest {
        insertRestaurant("cloud-restaurant")
        database.inventoryAreaDao().upsert(
            InventoryAreaEntity(
                id = "existing-area",
                restaurantId = "cloud-restaurant",
                name = "Existing Area",
                normalizedName = "existing area",
                sortOrder = 0,
                isActive = true,
                createdAt = NOW,
                updatedAt = NOW,
                deletedAt = null
            )
        )

        val result = repository.completeSetup(command(RestaurantId("cloud-restaurant")))

        assertThat(result).isEqualTo(LocalSetupResult.AlreadyCompleted)
        assertThat(database.inventoryAreaDao().getActiveCount("cloud-restaurant")).isEqualTo(1)
        assertThat(ids.calls).isEqualTo(0)
    }

    private suspend fun insertRestaurant(id: String, name: String = "Existing"): RestaurantEntity {
        val restaurant = RestaurantEntity(
            id = id,
            name = name,
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = NOW,
            updatedAt = NOW,
            deletedAt = null
        )
        database.restaurantDao().insert(restaurant)
        return restaurant
    }

    private fun command(restaurantId: RestaurantId? = null) = CompleteLocalSetupCommand(
        restaurantName = "Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        areas = listOf(SetupAreaInput(name = "Kitchen", sortOrder = 0)),
        categories = listOf(SetupCategoryInput(name = "Food", sortOrder = 0)),
        restaurantId = restaurantId
    )

    private class QueueIdGenerator : IdGenerator {
        private val values = ArrayDeque<String>()
        var calls: Int = 0
            private set

        fun enqueue(vararg values: String) {
            this.values.addAll(values)
        }

        override fun newId(): String {
            calls += 1
            return values.removeFirst()
        }
    }

    private object FixedTimeProvider : TimeProvider {
        override fun now(): Instant = Instant.ofEpochMilli(NOW)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
