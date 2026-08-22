package com.venkoi.restaurantops.core.database.repository

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.dao.IngredientCategoryDao
import com.venkoi.restaurantops.core.database.mapper.toDomain
import com.venkoi.restaurantops.core.database.mapper.toEntity
import com.venkoi.restaurantops.core.database.sync.IngredientCategorySyncOutboxWriter
import com.venkoi.restaurantops.core.domain.repository.IngredientCategoryRepository
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomIngredientCategoryRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val categoryDao: IngredientCategoryDao,
    private val outboxWriter: IngredientCategorySyncOutboxWriter
) : IngredientCategoryRepository {
    constructor(
        database: RestaurantInventoryDatabase,
        categoryDao: IngredientCategoryDao
    ) : this(
        database,
        categoryDao,
        IngredientCategorySyncOutboxWriter(
            database.syncEntityMetadataDao(),
            database.syncOutboxDao(),
            com.venkoi.restaurantops.core.common.ids.UuidIdGenerator(),
            com.venkoi.restaurantops.core.common.time.SystemTimeProvider(),
            kotlinx.serialization.json.Json { encodeDefaults = true }
        )
    )
    override fun observeActiveCategories(): Flow<List<IngredientCategory>> {
        return categoryDao.observeActiveCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllCategories(): Flow<List<IngredientCategory>> {
        return categoryDao.observeAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllCategoriesForRestaurant(restaurantId: com.venkoi.restaurantops.core.common.ids.RestaurantId): List<IngredientCategory> =
        categoryDao.getAllCategoriesForRestaurant(restaurantId.value).map { it.toDomain() }

    override suspend fun getById(id: IngredientCategoryId): IngredientCategory? {
        return categoryDao.getById(id.value)?.toDomain()
    }

    override suspend fun save(category: IngredientCategory) {
        val normalizedName = category.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName

        database.withTransaction {
            val duplicate = categoryDao.findByNormalizedName(category.restaurantId.value, normalizedName)
            if (duplicate != null && duplicate.id != category.id.value) {
                throw ValidationError.DuplicateActiveName
            }
            val persisted = category.copy(normalizedName = normalizedName).toEntity()
            categoryDao.upsert(persisted)
            outboxWriter.record(persisted)
        }
    }

    override suspend fun archive(id: IngredientCategoryId, at: Instant) {
        database.withTransaction {
            categoryDao.getById(id.value) ?: throw ValidationError.RecordNotFound
            categoryDao.softArchive(id.value, at.toEpochMilli())
            val tombstone = categoryDao.getById(id.value)!!
            outboxWriter.record(tombstone)
        }
    }

    override suspend fun reorder(ids: List<IngredientCategoryId>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            val firstEntity = categoryDao.getById(ids.first().value) ?: throw ValidationError.InvalidSetupState
            val restaurantId = firstEntity.restaurantId
            
            val activeIds = categoryDao.getActiveIds(restaurantId).toSet()
            val inputIds = ids.map { it.value }.toSet()
            
            if (inputIds.size != ids.size) throw ValidationError.InvalidSetupState
            if (inputIds != activeIds) throw ValidationError.InvalidSetupState
            
            ids.forEachIndexed { index, id ->
                val entity = categoryDao.getById(id.value)!!
                if (entity.sortOrder != index) {
                    val reordered = entity.copy(sortOrder = index)
                    categoryDao.upsert(reordered)
                    outboxWriter.record(reordered)
                }
            }
        }
    }
}
