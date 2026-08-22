package com.venkoi.restaurantops.core.database.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.UuidIdGenerator
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import com.venkoi.restaurantops.core.database.repository.RoomIngredientCategoryRepository
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class IngredientCategoryLocalSyncFoundationTest {
    private lateinit var db: RestaurantInventoryDatabase
    private val json = Json { encodeDefaults = true }
    private val ids = QueueIds()
    private lateinit var writer: IngredientCategorySyncOutboxWriter
    private lateinit var repository: RoomIngredientCategoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        writer = IngredientCategorySyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), ids, FixedTime, json
        )
        repository = RoomIngredientCategoryRepository(db, db.ingredientCategoryDao(), writer)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `save persists normalized snapshot with zero or known base`() = runTest {
        restaurant()
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString())

        repository.save(category(name = " Fresh Produce ", updatedAt = 10))
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(
                INGREDIENT_CATEGORY_ENTITY_TYPE, "category-1", "restaurant-1", 4, 20
            )
        )
        repository.save(category(name = "Produce", updatedAt = 11))

        val persisted = db.ingredientCategoryDao().getById("category-1")!!
        assertThat(persisted.normalizedName).isEqualTo("produce")
        val operations = operations("category-1")
        assertThat(operations).hasSize(2)
        assertThat(operations.map { it.baseServerVersion }).containsExactly(0L, 4L).inOrder()
        assertThat(operations[0].operationId).isNotEqualTo(operations[1].operationId)
        assertUuidV4(operations[0].operationId)
        val first = json.decodeFromString<IngredientCategorySyncPayload>(operations[0].payloadJson)
        val second = json.decodeFromString<IngredientCategorySyncPayload>(operations[1].payloadJson)
        assertThat(first).isEqualTo(
            IngredientCategorySyncPayload(
                persisted.id, persisted.restaurantId, " Fresh Produce ", "fresh produce", 0,
                true, 1, 10, null
            )
        )
        assertThat(second.name).isEqualTo("Produce")
        assertThat(second.updatedAt).isEqualTo(11)
    }

    @Test
    fun `outbox failure rolls back save and business failure emits no outbox`() = runTest {
        restaurant()
        val repeated = UUID.randomUUID().toString()
        ids.add(repeated, repeated)
        repository.save(category(id = "category-1", name = "Produce"))

        val outboxFailure = runCatching {
            repository.save(category(id = "category-2", name = "Meat"))
        }
        assertThat(outboxFailure.isFailure).isTrue()
        assertThat(db.ingredientCategoryDao().getById("category-2")).isNull()

        val businessFailure = runCatching {
            repository.save(category(id = "category-3", name = " Produce "))
        }
        assertThat(businessFailure.isFailure).isTrue()
        assertThat(db.ingredientCategoryDao().getById("category-3")).isNull()
        assertThat(operations("category-3")).isEmpty()
    }

    @Test
    fun `archive records exact persisted tombstone`() = runTest {
        restaurant()
        db.ingredientCategoryDao().upsert(entity("category-1", 0))
        ids.add(UUID.randomUUID().toString())

        repository.archive(IngredientCategoryId("category-1"), Instant.ofEpochMilli(99))

        val persisted = db.ingredientCategoryDao().getById("category-1")!!
        val payload = json.decodeFromString<IngredientCategorySyncPayload>(
            operations("category-1").single().payloadJson
        )
        assertThat(persisted.isActive).isFalse()
        assertThat(persisted.deletedAt).isEqualTo(99)
        assertThat(payload.isActive).isFalse()
        assertThat(payload.deletedAt).isEqualTo(99)
    }

    @Test
    fun `reorder records only categories whose persisted order changed`() = runTest {
        restaurant()
        db.ingredientCategoryDao().upsert(entity("a", 0))
        db.ingredientCategoryDao().upsert(entity("b", 1))
        db.ingredientCategoryDao().upsert(entity("c", 2))
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString())

        repository.reorder(
            listOf(IngredientCategoryId("b"), IngredientCategoryId("a"), IngredientCategoryId("c"))
        )

        assertThat(db.ingredientCategoryDao().getById("b")?.sortOrder).isEqualTo(0)
        assertThat(db.ingredientCategoryDao().getById("a")?.sortOrder).isEqualTo(1)
        assertThat(operations("a")).hasSize(1)
        assertThat(operations("b")).hasSize(1)
        assertThat(operations("c")).isEmpty()
        assertThat(
            json.decodeFromString<IngredientCategorySyncPayload>(operations("b").single().payloadJson)
                .sortOrder
        ).isEqualTo(0)
    }

    @Test
    fun `three sequential edits retain FIFO identities and immutable payloads`() = runTest {
        restaurant()
        val operationIds = List(3) { UUID.randomUUID().toString() }
        ids.add(*operationIds.toTypedArray())

        repository.save(category(name = "One", updatedAt = 1))
        repository.save(category(name = "Two", updatedAt = 2))
        repository.save(category(name = "Three", updatedAt = 3))

        val operations = operations("category-1")
        assertThat(operations.map { it.operationId }).containsExactlyElementsIn(operationIds).inOrder()
        assertThat(operations.map { it.localSequence }).isInStrictOrder()
        assertThat(operations.map { it.baseServerVersion }).containsExactly(0L, 0L, 0L).inOrder()
        assertThat(operations.map {
            json.decodeFromString<IngredientCategorySyncPayload>(it.payloadJson).name
        }).containsExactly("One", "Two", "Three").inOrder()
    }

    @Test
    fun `preparation includes tombstones and is idempotent while skipping metadata or pending`() = runTest {
        restaurant()
        db.ingredientCategoryDao().upsert(entity("has-metadata", 0))
        db.ingredientCategoryDao().upsert(entity("has-pending", 1))
        db.ingredientCategoryDao().upsert(entity("needs-op", 2))
        db.ingredientCategoryDao().upsert(
            entity("archived", 3).copy(isActive = false, deletedAt = 50)
        )
        db.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(
                INGREDIENT_CATEGORY_ENTITY_TYPE, "has-metadata", "restaurant-1", 1, 1
            )
        )
        ids.add(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
        writer.record(entity("has-pending", 1))
        val preparation = IngredientCategorySyncPreparation(db, writer)

        preparation.prepareUnsyncedIngredientCategories("restaurant-1")
        preparation.prepareUnsyncedIngredientCategories("restaurant-1")

        assertThat(operations("has-metadata")).isEmpty()
        assertThat(operations("has-pending")).hasSize(1)
        assertThat(operations("needs-op")).hasSize(1)
        val tombstone = json.decodeFromString<IngredientCategorySyncPayload>(
            operations("archived").single().payloadJson
        )
        assertThat(tombstone.isActive).isFalse()
        assertThat(tombstone.deletedAt).isEqualTo(50)
    }

    @Test
    fun `real writer generates UUID v4 without modifying cursor`() = runTest {
        restaurant()
        val realWriter = IngredientCategorySyncOutboxWriter(
            db.syncEntityMetadataDao(), db.syncOutboxDao(), UuidIdGenerator(), FixedTime, json
        )
        realWriter.record(entity("category-1", 0))
        assertUuidV4(operations("category-1").single().operationId)
        assertThat(db.syncCursorDao().get("restaurant-1", INGREDIENT_CATEGORY_ENTITY_TYPE)).isNull()
    }

    private suspend fun restaurant() = db.restaurantDao().insert(
        RestaurantEntity("restaurant-1", "Restaurant", "USD", "en-US", 1, 1, null)
    )

    private fun entity(id: String, order: Int) = IngredientCategoryEntity(
        id, "restaurant-1", id, id, order, true, 1, 1, null
    )

    private fun category(
        id: String = "category-1",
        name: String,
        updatedAt: Long = 1
    ) = IngredientCategory(
        IngredientCategoryId(id), RestaurantId("restaurant-1"), name, "stale", 0, true,
        Instant.ofEpochMilli(1), Instant.ofEpochMilli(updatedAt), null
    )

    private suspend fun operations(id: String) =
        db.syncOutboxDao().getForEntity(INGREDIENT_CATEGORY_ENTITY_TYPE, id)

    private fun assertUuidV4(value: String) {
        assertThat(UUID.fromString(value).version()).isEqualTo(4)
    }

    private class QueueIds : IdGenerator {
        private val values = ArrayDeque<String>()
        fun add(vararg value: String) = values.addAll(value)
        override fun newId(): String = values.removeFirst()
    }

    private object FixedTime : TimeProvider {
        override fun now(): Instant = Instant.ofEpochMilli(1234)
    }
}
