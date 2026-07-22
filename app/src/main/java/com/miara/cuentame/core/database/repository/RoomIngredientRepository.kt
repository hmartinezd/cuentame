package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomIngredientRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val unitDao: UnitDao
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

    override suspend fun updateIngredient(ingredient: Ingredient) {
        val existing = ingredientDao.getById(ingredient.id.value)?.toDomain()
            ?: throw ValidationError.IngredientNotFound

        validateIngredient(ingredient)
        
        if (existing.baseUnitId != ingredient.baseUnitId) {
            throw ValidationError.IngredientBaseUnitImmutable
        }

        ingredientDao.upsert(ingredient.copy(normalizedName = ingredient.name.normalizeName()).toEntity())
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
        val ingredient = ingredientDao.getById(option.ingredientId.value)?.toDomain()
            ?: throw ValidationError.IngredientNotFound
        if (!ingredient.isActive) throw ValidationError.ArchivedReference

        validateUnitOption(option, ingredient)

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
        val option = unitOptionDao.getById(id.value)?.toDomain() ?: throw ValidationError.UnitOptionNotFound
        if (option.isBase) throw ValidationError.BaseUnitOptionCannotBeArchived
        
        if (option.isDefaultCount || option.isDefaultPurchase) {
            throw ValidationError.DefaultUnitOptionCannotBeArchived
        }

        unitOptionDao.softArchive(id.value, at.toEpochMilli())
    }

    override suspend fun createIngredientWithBaseOption(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption>
    ) {
        if (ingredient.id != baseOption.ingredientId) throw ValidationError.InvalidDefaultUnitOption
        if (!baseOption.isBase) throw ValidationError.MissingBaseUnitOption
        if (baseOption.factorToBase.compareTo(BigDecimal.ONE) != 0) throw ValidationError.InvalidBaseUnitFactor
        if (!baseOption.isActive) throw ValidationError.ArchivedReference
        if (baseOption.standardUnitId != ingredient.baseUnitId) throw ValidationError.InvalidBaseUnitFactor

        validateIngredient(ingredient)
        
        val standardUnit = unitDao.getById(ingredient.baseUnitId.value)
            ?: throw ValidationError.ArchivedReference
        if (standardUnit.factorToCanonical.toBigDecimal().compareTo(BigDecimal.ZERO) <= 0) {
            throw ValidationError.InvalidUnitFactor
        }

        database.withTransaction {
            ingredientDao.upsert(ingredient.copy(normalizedName = ingredient.name.normalizeName()).toEntity())
            unitOptionDao.upsert(baseOption.toEntity())
            
            additionalOptions.forEach { opt ->
                if (opt.ingredientId != ingredient.id) throw ValidationError.InvalidDefaultUnitOption
                validateUnitOption(opt, ingredient)
                unitOptionDao.upsert(opt.toEntity())
            }
        }
    }

    private suspend fun validateIngredient(ingredient: Ingredient) {
        val normalized = ingredient.name.normalizeName()
        if (normalized.isBlank()) throw ValidationError.InvalidName
        val duplicate = ingredientDao.findByNormalizedName(ingredient.restaurantId.value, normalized)
        if (duplicate != null && duplicate.id != ingredient.id.value) throw ValidationError.DuplicateActiveName
    }

    private suspend fun validateUnitOption(option: IngredientUnitOption, ingredient: Ingredient) {
        if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor
        
        val existing = unitOptionDao.getById(option.id.value)?.toDomain()
        
        if (existing != null) {
            if (existing.ingredientId != option.ingredientId) {
                throw ValidationError.InvalidDefaultUnitOption // Cannot move to another ingredient
            }
            if (existing.isBase) {
                // Cannot degrade base option
                if (!option.isBase || !option.isActive || option.deletedAt != null || 
                    option.factorToBase.compareTo(BigDecimal.ONE) != 0 || 
                    option.standardUnitId != ingredient.baseUnitId) {
                    throw ValidationError.BaseUnitOptionCannotBeModified
                }
            }
        }

        if (option.isBase) {
            if (option.factorToBase.compareTo(BigDecimal.ONE) != 0) throw ValidationError.InvalidBaseUnitFactor
            if (option.standardUnitId != ingredient.baseUnitId) throw ValidationError.InvalidBaseUnitFactor
        }

        if (option.standardUnitId != null) {
            val unit = unitDao.getById(option.standardUnitId.value) ?: throw ValidationError.ArchivedReference
            val baseUnit = unitDao.getById(ingredient.baseUnitId.value) ?: throw ValidationError.ArchivedReference
            if (unit.dimension != baseUnit.dimension) throw ValidationError.IncompatibleUnitDimensions
            
            // Check if standard unit already added (other than itself)
            val activeOptions = unitOptionDao.getActiveOptions(option.ingredientId.value)
            if (activeOptions.any { it.standardUnitId == option.standardUnitId.value && it.id != option.id.value }) {
                throw ValidationError.StandardUnitAlreadyAdded
            }
        } else {
            // Package option - check name uniqueness within ingredient
            val activeOptions = unitOptionDao.getActiveOptions(option.ingredientId.value)
            val normalizedName = option.displayName.normalizeName()
            if (activeOptions.any { it.displayName.normalizeName() == normalizedName && it.id != option.id.value }) {
                throw ValidationError.UnitOptionNameAlreadyExists
            }
        }

        if (!option.isActive && (option.isDefaultCount || option.isDefaultPurchase)) {
            throw ValidationError.InvalidDefaultUnitOption
        }
        
        if (option.isBase && option.isActive) {
            val activeBases = unitOptionDao.getActiveBaseOptions(ingredient.id.value)
            if (activeBases.any { it.id != option.id.value }) {
                throw ValidationError.MultipleBaseUnitOptions
            }
        }
    }
}
