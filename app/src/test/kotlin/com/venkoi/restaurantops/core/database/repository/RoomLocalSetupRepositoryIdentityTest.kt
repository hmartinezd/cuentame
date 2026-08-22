package com.venkoi.restaurantops.core.database.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity
import com.venkoi.restaurantops.core.database.sync.INGREDIENT_CATEGORY_ENTITY_TYPE
import com.venkoi.restaurantops.core.database.sync.IngredientCategorySyncOutboxWriter
import com.venkoi.restaurantops.core.database.sync.IngredientCategorySyncPayload
import com.venkoi.restaurantops.core.database.sync.INVENTORY_AREA_ENTITY_TYPE
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncOutboxWriter
import com.venkoi.restaurantops.core.domain.repository.CompleteLocalSetupCommand
import com.venkoi.restaurantops.core.domain.repository.LocalSetupResult
import com.venkoi.restaurantops.core.domain.repository.SetupAreaInput
import com.venkoi.restaurantops.core.domain.repository.SetupCategoryInput
import com.venkoi.restaurantops.core.domain.usecase.LocalSetupValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
        val operation = database.syncOutboxDao()
            .getPending("cloud-restaurant", INVENTORY_AREA_ENTITY_TYPE, 10)
            .single()
        assertThat(operation.restaurantId).isEqualTo("cloud-restaurant")
        assertThat(operation.entityId).isEqualTo("area-1")
        val category = database.ingredientCategoryDao().getById("category-1")!!
        val categoryOperation = database.syncOutboxDao()
            .getForEntity(INGREDIENT_CATEGORY_ENTITY_TYPE, category.id).single()
        assertThat(categoryOperation.restaurantId).isEqualTo(category.restaurantId)
        assertThat(categoryOperation.entityId).isEqualTo(category.id)
        assertThat(categoryOperation.baseServerVersion).isEqualTo(0)
        assertThat(Json.decodeFromString<IngredientCategorySyncPayload>(categoryOperation.payloadJson))
            .isEqualTo(category.toSyncPayload())
    }

    @Test
    fun `category outbox failure rolls back entire local setup transaction`() = runTest {
        val repeatedOperationId = "11111111-1111-4111-8111-111111111111"
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                operationId = repeatedOperationId,
                restaurantId = "preexisting",
                entityType = "TEST",
                entityId = "preexisting",
                baseServerVersion = 0,
                payloadJson = "{}",
                createdAt = NOW
            )
        )
        val normalWriterIds = object : IdGenerator {
            override fun newId() = "22222222-2222-4222-8222-222222222222"
        }
        val duplicateWriterIds = object : IdGenerator {
            override fun newId() = repeatedOperationId
        }
        val failingRepository = RoomLocalSetupRepository(
            database,
            database.restaurantDao(),
            database.inventoryAreaDao(),
            database.ingredientCategoryDao(),
            ids,
            FixedTimeProvider,
            LocalSetupValidator(),
            InventoryAreaSyncOutboxWriter(
                database.syncEntityMetadataDao(), database.syncOutboxDao(), normalWriterIds,
                FixedTimeProvider, Json { encodeDefaults = true }
            ),
            IngredientCategorySyncOutboxWriter(
                database.syncEntityMetadataDao(), database.syncOutboxDao(), duplicateWriterIds,
                FixedTimeProvider, Json { encodeDefaults = true }
            )
        )
        ids.enqueue("area-1", "category-1")

        val result = failingRepository.completeSetup(command(RestaurantId("cloud-restaurant")))

        assertThat(result).isInstanceOf(LocalSetupResult.Failure::class.java)
        assertThat(database.restaurantDao().getRestaurant()).isNull()
        assertThat(database.inventoryAreaDao().getActiveCount("cloud-restaurant")).isEqualTo(0)
        assertThat(database.ingredientCategoryDao().getAllCategoriesForRestaurant("cloud-restaurant"))
            .isEmpty()
        assertThat(database.syncOutboxDao().getAll().map { it.operationId })
            .containsExactly(repeatedOperationId)
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

    private fun com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity.toSyncPayload() =
        IngredientCategorySyncPayload(
            id, restaurantId, name, normalizedName, sortOrder, isActive,
            createdAt, updatedAt, deletedAt
        )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
