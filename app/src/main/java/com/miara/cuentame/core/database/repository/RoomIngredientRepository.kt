package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
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

    override fun observeIngredients(includeArchived: Boolean): Flow<List<Ingredient>> {
        val flow = if (includeArchived) {
            ingredientDao.observeAllIngredients()
        } else {
            ingredientDao.observeActiveIngredients()
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeIngredient(id: IngredientId): Flow<Ingredient?> {
        return ingredientDao.observeIngredient(id.value).map { it?.toDomain() }
    }

    override suspend fun getById(id: IngredientId): Ingredient? {
        return ingredientDao.getById(id.value)?.toDomain()
    }

    override suspend fun updateIngredient(ingredient: Ingredient) {
        database.withTransaction {
            val existing = ingredientDao.getById(ingredient.id.value)?.toDomain()
                ?: throw ValidationError.IngredientNotFound
            
            if (existing.deletedAt != null) throw ValidationError.ArchivedReference
            if (existing.restaurantId != ingredient.restaurantId) throw ValidationError.IngredientBaseUnitImmutable // Placeholder for "cannot change restaurant"
            if (existing.baseUnitId != ingredient.baseUnitId) throw ValidationError.IngredientBaseUnitImmutable
            
            validateIngredientCore(ingredient)
            
            if (ingredient.categoryId != null) {
                val category = categoryDao.getById(ingredient.categoryId.value)
                    ?: throw ValidationError.RecordNotFound
                if (!category.isActive || category.deletedAt != null) throw ValidationError.ArchivedReference
                if (category.restaurantId != ingredient.restaurantId.value) throw ValidationError.RecordNotFound
            }

            ingredientDao.upsert(ingredient.copy(
                normalizedName = ingredient.name.normalizeName(),
                updatedAt = timeProvider.now(),
                createdAt = existing.createdAt // Preserve createdAt
            ).toEntity())
        }
    }

    override suspend fun archive(id: IngredientId, at: Instant) {
        val existing = ingredientDao.getById(id.value) ?: throw ValidationError.IngredientNotFound
        ingredientDao.softArchive(id.value, at.toEpochMilli())
    }

    override fun observeUnitOptions(ingredientId: IngredientId): Flow<List<IngredientUnitOption>> {
        return unitOptionDao.observeOptionsForIngredient(ingredientId.value).map { entities ->
            entities.map { it.toDomain() }
        }
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
            
            if (baseUnit.dimension != targetUnit.dimension) throw ValidationError.IncompatibleUnitDimensions
            
            val factor = converter.convert(BigDecimal.ONE, targetUnit, baseUnit)
            
            val activeOptions = unitOptionDao.getActiveOptions(ingredient.id.value)
            if (activeOptions.any { it.standardUnitId == command.standardUnitId.value }) {
                throw ValidationError.StandardUnitAlreadyAdded
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
            
            unitOptionDao.upsert(option.toEntity())
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
            
            unitOptionDao.upsert(option.toEntity())
        }
    }

    override suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand) {
        database.withTransaction {
            val existing = unitOptionDao.getById(command.optionId.value)?.toDomain()
                ?: throw ValidationError.UnitOptionNotFound
            if (existing.isBase || existing.standardUnitId != null) throw ValidationError.BaseUnitOptionCannotBeModified
            
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

            unitOptionDao.upsert(existing.copy(
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
            unitOptionDao.upsert(option.copy(isDefaultCount = true, updatedAt = timeProvider.now()).toEntity())
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
            unitOptionDao.upsert(option.copy(isDefaultPurchase = true, updatedAt = timeProvider.now()).toEntity())
        }
    }

    override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {
        database.withTransaction {
            val option = unitOptionDao.getById(id.value)?.toDomain() ?: throw ValidationError.UnitOptionNotFound
            if (option.isBase) throw ValidationError.BaseUnitOptionCannotBeArchived
            if (option.isDefaultCount || option.isDefaultPurchase) throw ValidationError.DefaultUnitOptionCannotBeArchived
            
            unitOptionDao.softArchive(id.value, at.toEpochMilli())
        }
    }

    override suspend fun createIngredientWithBaseOption(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption>
    ) {
        database.withTransaction {
            val restaurant = restaurantDao.getRestaurant()?.toDomain() ?: throw ValidationError.RecordNotFound
            if (restaurant.id != ingredient.restaurantId) throw ValidationError.RecordNotFound
            
            // Validate Ingredient
            validateIngredientCore(ingredient)
            
            // Validate Category
            if (ingredient.categoryId != null) {
                val category = categoryDao.getById(ingredient.categoryId.value)
                    ?: throw ValidationError.RecordNotFound
                if (!category.isActive || category.deletedAt != null) throw ValidationError.ArchivedReference
                if (category.restaurantId != restaurant.id.value) throw ValidationError.RecordNotFound
            }

            // Validate Base Unit
            val baseUnit = unitDao.getById(ingredient.baseUnitId.value)
                ?: throw ValidationError.RecordNotFound
            if (baseUnit.factorToCanonical <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

            // All options as one set
            val allOptions = listOf(baseOption) + additionalOptions
            
            // Validate Options
            val optionIds = allOptions.map { it.id }.toSet()
            if (optionIds.size != allOptions.size) throw ValidationError.InvalidDefaultUnitOption // Duplicate ID
            
            var baseCount = 0
            var countDefaultCount = 0
            var purchaseDefaultCount = 0
            val normalizedNames = mutableSetOf<String>()
            val standardUnitsUsed = mutableSetOf<UnitId>()

            allOptions.forEach { opt ->
                if (opt.ingredientId != ingredient.id) throw ValidationError.InvalidDefaultUnitOption
                if (opt.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor
                
                val norm = opt.displayName.normalizeName()
                if (norm.isBlank()) throw ValidationError.InvalidName
                if (!normalizedNames.add(norm)) throw ValidationError.UnitOptionNameAlreadyExists
                
                if (opt.isBase) {
                    baseCount++
                    if (opt.factorToBase.compareTo(BigDecimal.ONE) != 0) throw ValidationError.InvalidBaseUnitFactor
                    if (opt.standardUnitId != ingredient.baseUnitId) throw ValidationError.InvalidBaseUnitFactor
                    if (!opt.isActive || opt.deletedAt != null) throw ValidationError.BaseUnitOptionCannotBeArchived
                } else {
                    if (opt.standardUnitId != null) {
                        if (!standardUnitsUsed.add(opt.standardUnitId)) throw ValidationError.StandardUnitAlreadyAdded
                        val sUnit = unitDao.getById(opt.standardUnitId.value) ?: throw ValidationError.RecordNotFound
                        if (sUnit.dimension != baseUnit.dimension) throw ValidationError.IncompatibleUnitDimensions
                        
                        // Derive and check factor
                        val derivedFactor = converter.convert(BigDecimal.ONE, sUnit.toDomain(), baseUnit.toDomain())
                        if (opt.factorToBase.compareTo(derivedFactor) != 0) throw ValidationError.InvalidStandardUnitFactor
                    }
                }
                
                if (opt.isDefaultCount) {
                    countDefaultCount++
                    if (!opt.isActive || opt.deletedAt != null) throw ValidationError.InvalidDefaultUnitOption
                }
                if (opt.isDefaultPurchase) {
                    purchaseDefaultCount++
                    if (!opt.isActive || opt.deletedAt != null) throw ValidationError.InvalidDefaultUnitOption
                }
            }

            if (baseCount != 1) throw ValidationError.MissingBaseUnitOption
            if (countDefaultCount != 1) throw ValidationError.InvalidDefaultUnitOption
            if (purchaseDefaultCount != 1) throw ValidationError.InvalidDefaultUnitOption

            // Persist
            ingredientDao.upsert(ingredient.copy(normalizedName = ingredient.name.normalizeName()).toEntity())
            allOptions.forEach { unitOptionDao.upsert(it.toEntity()) }
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
