package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.UpdateIngredientCommand
import com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand
import com.miara.cuentame.core.domain.service.StandardUnitConverter
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
    private val unitDao: UnitDao,
    private val restaurantDao: RestaurantDao,
    private val categoryDao: IngredientCategoryDao,
    private val converter: StandardUnitConverter,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : IngredientRepository {

    override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>> {
        val flow = if (includeArchived) {
            ingredientDao.observeAllIngredients(restaurantId.value)
        } else {
            ingredientDao.observeActiveIngredients(restaurantId.value)
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean): List<Ingredient> {
        val entities = if (includeArchived) {
            ingredientDao.getAllIngredients(restaurantId.value)
        } else {
            ingredientDao.getActiveIngredients(restaurantId.value)
        }
        return entities.map { it.toDomain() }
    }

    override fun observeIngredient(id: IngredientId): Flow<Ingredient?> {
        return ingredientDao.observeIngredient(id.value).map { it?.toDomain() }
    }

    override suspend fun getById(id: IngredientId): Ingredient? {
        return ingredientDao.getById(id.value)?.toDomain()
    }

    override suspend fun updateIngredient(command: UpdateIngredientCommand) {
        database.withTransaction {
            val existing = ingredientDao.getById(command.ingredientId.value)?.toDomain()
                ?: throw ValidationError.IngredientNotFound
            
            if (existing.deletedAt != null || !existing.isActive) throw ValidationError.ArchivedReference
            
            val normalizedName = command.name.normalizeName()
            if (normalizedName.isBlank()) throw ValidationError.InvalidName
            
            val duplicate = ingredientDao.findByNormalizedName(existing.restaurantId.value, normalizedName)
            if (duplicate != null && duplicate.id != command.ingredientId.value) {
                throw ValidationError.DuplicateActiveName
            }
            
            if (command.categoryId != null) {
                val category = categoryDao.getById(command.categoryId.value)
                    ?: throw ValidationError.RecordNotFound
                if (!category.isActive || category.deletedAt != null) throw ValidationError.ArchivedReference
                if (category.restaurantId != existing.restaurantId.value) throw ValidationError.IngredientOwnershipMismatch
            }

            ingredientDao.update(existing.copy(
                name = command.name,
                normalizedName = normalizedName,
                categoryId = command.categoryId,
                updatedAt = timeProvider.now()
            ).toEntity())
        }
    }

    override suspend fun archive(id: IngredientId, at: Instant) {
        val existing = ingredientDao.getById(id.value) ?: throw ValidationError.IngredientNotFound
        if (existing.deletedAt != null) return // Idempotent
        ingredientDao.softArchive(id.value, at.toEpochMilli())
    }

    override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): Flow<List<IngredientUnitOption>> {
        val flow = if (includeArchived) {
            unitOptionDao.observeAllOptionsForIngredient(ingredientId.value)
        } else {
            unitOptionDao.observeActiveOptionsForIngredient(ingredientId.value)
        }
        return flow.map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): List<IngredientUnitOption> {
        val entities = if (includeArchived) {
            unitOptionDao.getAllOptions(ingredientId.value)
        } else {
            unitOptionDao.getActiveOptions(ingredientId.value)
        }
        return entities.map { it.toDomain() }
    }

    override suspend fun addStandardUnitOption(command: AddStandardUnitOptionCommand) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(command.ingredientId.value)?.toDomain()
                ?: throw ValidationError.IngredientNotFound
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

            val baseUnit = unitDao.getById(ingredient.baseUnitId.value)?.toDomain()
                ?: throw ValidationError.RecordNotFound
            val targetUnit = unitDao.getById(command.standardUnitId.value)?.toDomain()
                ?: throw ValidationError.RecordNotFound
            
            if (!targetUnit.isSystem) throw ValidationError.RecordNotFound
            if (baseUnit.dimension != targetUnit.dimension) throw ValidationError.IncompatibleUnitDimensions
            if (targetUnit.id == baseUnit.id) throw ValidationError.StandardUnitAlreadyAdded
            
            val factor = converter.convert(BigDecimal.ONE, targetUnit, baseUnit)
            
            val activeOptions = unitOptionDao.getActiveOptions(ingredient.id.value)
            if (activeOptions.any { it.standardUnitId == command.standardUnitId.value }) {
                throw ValidationError.StandardUnitAlreadyAdded
            }

            val normalizedDisplayName = targetUnit.symbol.normalizeName()
            if (activeOptions.any { it.displayName.normalizeName() == normalizedDisplayName }) {
                throw ValidationError.UnitOptionNameAlreadyExists
            }

            val now = timeProvider.now()
            val option = IngredientUnitOption(
                id = IngredientUnitOptionId(idGenerator.newId()),
                ingredientId = ingredient.id,
                displayName = targetUnit.symbol,
                shortLabel = targetUnit.symbol,
                standardUnitId = targetUnit.id,
                factorToBase = factor,
                isBase = false,
                isDefaultCount = command.isDefaultCount,
                isDefaultPurchase = command.isDefaultPurchase,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            if (option.isDefaultCount) unitOptionDao.clearDefaultCount(ingredient.id.value)
            if (option.isDefaultPurchase) unitOptionDao.clearDefaultPurchase(ingredient.id.value)
            
            unitOptionDao.insert(option.toEntity())
        }
    }

    override suspend fun addPackageUnitOption(command: AddPackageUnitOptionCommand) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(command.ingredientId.value)?.toDomain()
                ?: throw ValidationError.IngredientNotFound
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

            if (command.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidPackageQuantity
            val normalizedName = command.displayName.normalizeName()
            if (normalizedName.isBlank()) throw ValidationError.InvalidName
            
            val activeOptions = unitOptionDao.getActiveOptions(ingredient.id.value)
            if (activeOptions.any { it.displayName.normalizeName() == normalizedName }) {
                throw ValidationError.UnitOptionNameAlreadyExists
            }

            val now = timeProvider.now()
            val option = IngredientUnitOption(
                id = IngredientUnitOptionId(idGenerator.newId()),
                ingredientId = ingredient.id,
                displayName = command.displayName,
                shortLabel = command.displayName,
                standardUnitId = null,
                factorToBase = command.factorToBase,
                isBase = false,
                isDefaultCount = command.isDefaultCount,
                isDefaultPurchase = command.isDefaultPurchase,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            if (option.isDefaultCount) unitOptionDao.clearDefaultCount(ingredient.id.value)
            if (option.isDefaultPurchase) unitOptionDao.clearDefaultPurchase(ingredient.id.value)
            
            unitOptionDao.insert(option.toEntity())
        }
    }

    override suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand) {
        database.withTransaction {
            val existing = unitOptionDao.getById(command.optionId.value)?.toDomain()
                ?: throw ValidationError.UnitOptionNotFound
            if (existing.isBase || existing.standardUnitId != null) throw ValidationError.BaseUnitOptionCannotBeModified
            if (!existing.isActive || existing.deletedAt != null) throw ValidationError.ArchivedReference
            
            val ingredient = ingredientDao.getById(existing.ingredientId.value)
                ?: throw ValidationError.IngredientNotFound
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

            if (command.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidPackageQuantity
            val normalizedName = command.displayName.normalizeName()
            if (normalizedName.isBlank()) throw ValidationError.InvalidName
            
            val activeOptions = unitOptionDao.getActiveOptions(existing.ingredientId.value)
            if (activeOptions.any { it.displayName.normalizeName() == normalizedName && it.id != existing.id.value }) {
                throw ValidationError.UnitOptionNameAlreadyExists
            }

            unitOptionDao.update(existing.copy(
                displayName = command.displayName,
                shortLabel = command.displayName,
                factorToBase = command.factorToBase,
                updatedAt = timeProvider.now()
            ).toEntity())
        }
    }

    override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(ingredientId.value) ?: throw ValidationError.IngredientNotFound
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference
            
            val option = unitOptionDao.getById(optionId.value)?.toDomain() ?: throw ValidationError.UnitOptionNotFound
            if (option.ingredientId != ingredientId) throw ValidationError.InvalidDefaultUnitOption
            if (!option.isActive || option.deletedAt != null) throw ValidationError.InvalidDefaultUnitOption
            
            unitOptionDao.clearDefaultCount(ingredientId.value)
            unitOptionDao.update(option.copy(isDefaultCount = true, updatedAt = timeProvider.now()).toEntity())
        }
    }

    override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(ingredientId.value) ?: throw ValidationError.IngredientNotFound
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference
            
            val option = unitOptionDao.getById(optionId.value)?.toDomain() ?: throw ValidationError.UnitOptionNotFound
            if (option.ingredientId != ingredientId) throw ValidationError.InvalidDefaultUnitOption
            if (!option.isActive || option.deletedAt != null) throw ValidationError.InvalidDefaultUnitOption
            
            unitOptionDao.clearDefaultPurchase(ingredientId.value)
            unitOptionDao.update(option.copy(isDefaultPurchase = true, updatedAt = timeProvider.now()).toEntity())
        }
    }

    override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {
        database.withTransaction {
            val option = unitOptionDao.getById(id.value)?.toDomain() ?: throw ValidationError.UnitOptionNotFound
            if (option.isBase) throw ValidationError.BaseUnitOptionCannotBeArchived
            if (option.isDefaultCount || option.isDefaultPurchase) throw ValidationError.DefaultUnitOptionCannotBeArchived
            if (option.deletedAt != null) return@withTransaction // Idempotent
            
            unitOptionDao.softArchive(id.value, at.toEpochMilli())
        }
    }

    override suspend fun createIngredientWithBaseOption(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption>
    ) {
        database.withTransaction {
            // Check IDs
            if (ingredientDao.getById(ingredient.id.value) != null) throw ValidationError.IngredientIdAlreadyExists
            if (unitOptionDao.getById(baseOption.id.value) != null) throw ValidationError.UnitOptionIdAlreadyExists
            additionalOptions.forEach { 
                if (unitOptionDao.getById(it.id.value) != null) throw ValidationError.UnitOptionIdAlreadyExists
            }

            // Validate Restaurant
            val restaurant = restaurantDao.getRestaurant()?.toDomain() ?: throw ValidationError.RecordNotFound
            if (restaurant.id != ingredient.restaurantId) throw ValidationError.IngredientOwnershipMismatch
            
            // Validate Ingredient
            validateIngredientCore(ingredient)
            
            // Validate Category
            if (ingredient.categoryId != null) {
                val category = categoryDao.getById(ingredient.categoryId.value)
                    ?: throw ValidationError.RecordNotFound
                if (!category.isActive || category.deletedAt != null) throw ValidationError.ArchivedReference
                if (category.restaurantId != restaurant.id.value) throw ValidationError.IngredientOwnershipMismatch
            }

            // Validate Base Unit
            val baseUnit = unitDao.getById(ingredient.baseUnitId.value)?.toDomain()
                ?: throw ValidationError.RecordNotFound
            if (!baseUnit.isSystem) throw ValidationError.RecordNotFound
            if (baseUnit.factorToCanonical <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

            // All options as one set
            val allOptions = listOf(baseOption) + additionalOptions
            
            // Validate IDs uniqueness within graph
            val optionIds = allOptions.map { it.id }.toSet()
            if (optionIds.size != allOptions.size) throw ValidationError.UnitOptionIdAlreadyExists
            
            // Strict Base Option Validation
            if (!baseOption.isBase) throw ValidationError.InvalidBaseUnitOption
            if (baseOption.ingredientId != ingredient.id) throw ValidationError.InvalidBaseUnitOption
            if (baseOption.standardUnitId != ingredient.baseUnitId) throw ValidationError.InvalidBaseUnitOption
            if (baseOption.factorToBase.compareTo(BigDecimal.ONE) != 0) throw ValidationError.InvalidBaseUnitOption
            if (!baseOption.isActive || baseOption.deletedAt != null) throw ValidationError.InvalidBaseUnitOption

            var countDefaultCount = 0
            var purchaseDefaultCount = 0
            val normalizedNames = mutableSetOf<String>()
            val standardUnitsUsed = mutableSetOf<UnitId>()

            allOptions.forEach { opt ->
                if (opt.ingredientId != ingredient.id) throw ValidationError.InvalidDefaultUnitOption
                if (opt.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor
                if (!opt.isActive || opt.deletedAt != null) throw ValidationError.ArchivedReference
                
                val norm = opt.displayName.normalizeName()
                if (norm.isBlank()) throw ValidationError.InvalidName
                if (!normalizedNames.add(norm)) throw ValidationError.UnitOptionNameAlreadyExists
                
                if (opt != baseOption) {
                    if (opt.isBase) throw ValidationError.AdditionalOptionCannotBeBase
                    
                    if (opt.standardUnitId != null) {
                        if (opt.standardUnitId == ingredient.baseUnitId) throw ValidationError.StandardUnitAlreadyAdded
                        if (!standardUnitsUsed.add(opt.standardUnitId)) throw ValidationError.StandardUnitAlreadyAdded
                        val sUnit = unitDao.getById(opt.standardUnitId.value)?.toDomain() ?: throw ValidationError.RecordNotFound
                        if (!sUnit.isSystem) throw ValidationError.RecordNotFound
                        if (sUnit.dimension != baseUnit.dimension) throw ValidationError.IncompatibleUnitDimensions
                        
                        // Derive and check factor
                        val derivedFactor = converter.convert(BigDecimal.ONE, sUnit, baseUnit)
                        if (opt.factorToBase.compareTo(derivedFactor) != 0) throw ValidationError.InvalidStandardUnitFactor
                    }
                }
                
                if (opt.isDefaultCount) countDefaultCount++
                if (opt.isDefaultPurchase) purchaseDefaultCount++
            }

            if (countDefaultCount != 1) throw ValidationError.InvalidDefaultUnitOption
            if (purchaseDefaultCount != 1) throw ValidationError.InvalidDefaultUnitOption

            // Persist
            ingredientDao.insert(ingredient.copy(normalizedName = ingredient.name.normalizeName()).toEntity())
            allOptions.forEach { unitOptionDao.insert(it.toEntity()) }
        }
    }

    private suspend fun validateIngredientCore(ingredient: Ingredient) {
        val normalized = ingredient.name.normalizeName()
        if (normalized.isBlank()) throw ValidationError.InvalidName
        val duplicate = ingredientDao.findByNormalizedName(ingredient.restaurantId.value, normalized)
        if (duplicate != null && duplicate.id != ingredient.id.value) throw ValidationError.DuplicateActiveName
        if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference
    }
}
