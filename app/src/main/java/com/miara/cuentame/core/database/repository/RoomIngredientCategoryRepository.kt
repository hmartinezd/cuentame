package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomIngredientCategoryRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val categoryDao: IngredientCategoryDao
) : IngredientCategoryRepository {
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

    override suspend fun getById(id: IngredientCategoryId): IngredientCategory? {
        return categoryDao.getById(id.value)?.toDomain()
    }

    override suspend fun save(category: IngredientCategory) {
        val normalizedName = category.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName

        val duplicate = categoryDao.findByNormalizedName(category.restaurantId.value, normalizedName)
        if (duplicate != null && duplicate.id != category.id.value) throw ValidationError.DuplicateActiveName

        categoryDao.upsert(category.copy(normalizedName = normalizedName).toEntity())
    }

    override suspend fun archive(id: IngredientCategoryId, at: Instant) {
        categoryDao.softArchive(id.value, at.toEpochMilli())
    }

    override suspend fun reorder(ids: List<IngredientCategoryId>) {
        database.withTransaction {
            ids.forEachIndexed { index, id ->
                val category = categoryDao.getById(id.value)
                if (category != null) {
                    categoryDao.upsert(category.copy(sortOrder = index))
                }
            }
        }
    }
}
