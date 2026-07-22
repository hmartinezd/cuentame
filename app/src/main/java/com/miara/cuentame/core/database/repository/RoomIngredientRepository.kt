package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomIngredientRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao
) : IngredientRepository {
    override fun observeActiveIngredients(): Flow<List<Ingredient>> {
        return ingredientDao.observeActiveIngredients().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeIngredient(id: IngredientId): Flow<Ingredient?> {
        return ingredientDao.observeIngredient(id.value).map { it?.toDomain() }
    }

    override suspend fun getById(id: IngredientId): Ingredient? {
        return ingredientDao.getById(id.value)?.toDomain()
    }

    override suspend fun save(ingredient: Ingredient) {
        val normalized = ingredient.copy(normalizedName = ingredient.name.normalizeName())
        ingredientDao.upsert(normalized.toEntity())
    }

    override suspend fun archive(id: IngredientId, at: Instant) {
        ingredientDao.softArchive(id.value, at.toEpochMilli())
    }

    override fun observeUnitOptions(ingredientId: IngredientId): Flow<List<IngredientUnitOption>> {
        return unitOptionDao.observeOptionsForIngredient(ingredientId.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveUnitOption(option: IngredientUnitOption) {
        database.withTransaction {
            if (option.isDefaultCount) {
                unitOptionDao.clearDefaultCount(option.ingredientId.value)
            }
            if (option.isDefaultPurchase) {
                unitOptionDao.clearDefaultPurchase(option.ingredientId.value)
            }
            unitOptionDao.upsert(option.toEntity())
        }
    }

    override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {
        unitOptionDao.softArchive(id.value, at.toEpochMilli())
    }
}
