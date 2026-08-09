package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.SupplierItemMappingDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.LearnMappingResult
import com.miara.cuentame.core.domain.repository.MappingConflict
import com.miara.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.supplier.SupplierItemMapping
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomSupplierItemMappingRepository @Inject constructor(
    private val mappingDao: SupplierItemMappingDao,
    private val supplierDao: SupplierDao,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val areaDao: InventoryAreaDao,
    private val idGenerator: IdGenerator
) : SupplierItemMappingRepository {

    override fun observeMappings(
        restaurantId: RestaurantId,
        supplierId: SupplierId
    ): Flow<List<SupplierItemMapping>> {
        return mappingDao.observeMappingsForSupplier(restaurantId.value, supplierId.value)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getMapping(
        restaurantId: RestaurantId,
        supplierId: SupplierId,
        keyType: SupplierItemMappingKeyType,
        normalizedKey: String
    ): SupplierItemMapping? {
        return mappingDao.getMapping(restaurantId.value, supplierId.value, keyType, normalizedKey)?.toDomain()
    }

    override suspend fun getMappingsForSupplier(
        restaurantId: RestaurantId,
        supplierId: SupplierId
    ): List<SupplierItemMapping> {
        // I need to add this query to mappingDao
        return mappingDao.getMappingsForSupplierSync(restaurantId.value, supplierId.value).map { it.toDomain() }
    }

    override suspend fun learnMapping(
        restaurantId: RestaurantId,
        supplierId: SupplierId,
        keyType: SupplierItemMappingKeyType,
        normalizedKey: String,
        sourceVendorCode: String?,
        sourceDescription: String?,
        sourcePackageText: String?,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?,
        force: Boolean
    ): LearnMappingResult {
        validateIntegrity(restaurantId, supplierId, ingredientId, unitOptionId, inventoryAreaId)
        
        val existing = getMapping(restaurantId, supplierId, keyType, normalizedKey)
        val now = Instant.now()

        if (existing != null) {
            val isSameTarget = existing.ingredientId == ingredientId &&
                    existing.unitOptionId == unitOptionId &&
                    existing.inventoryAreaId == inventoryAreaId

            if (isSameTarget) {
                val updated = existing.copy(lastConfirmedAt = now)
                mappingDao.insertMapping(updated.toEntity())
                return LearnMappingResult.NoChanges
            }

            if (!force) {
                return LearnMappingResult.Conflict(
                    MappingConflict(
                        existingMapping = existing,
                        newIngredientId = ingredientId,
                        newUnitOptionId = unitOptionId,
                        newInventoryAreaId = inventoryAreaId
                    )
                )
            }
            
            // Force update existing
            val updated = existing.copy(
                ingredientId = ingredientId,
                unitOptionId = unitOptionId,
                inventoryAreaId = inventoryAreaId,
                updatedAt = now,
                lastConfirmedAt = now,
                sourceVendorCode = sourceVendorCode ?: existing.sourceVendorCode,
                sourceDescription = sourceDescription ?: existing.sourceDescription,
                sourcePackageText = sourcePackageText ?: existing.sourcePackageText
            )
            mappingDao.insertMapping(updated.toEntity())
            return LearnMappingResult.Learned
        }

        // New mapping
        val newMapping = SupplierItemMapping(
            id = idGenerator.newId(),
            restaurantId = restaurantId,
            supplierId = supplierId,
            keyType = keyType,
            normalizedKey = normalizedKey,
            sourceVendorCode = sourceVendorCode,
            sourceDescription = sourceDescription,
            sourcePackageText = sourcePackageText,
            ingredientId = ingredientId,
            unitOptionId = unitOptionId,
            inventoryAreaId = inventoryAreaId,
            createdAt = now,
            updatedAt = now,
            lastConfirmedAt = now
        )
        mappingDao.insertMapping(newMapping.toEntity())
        return LearnMappingResult.Learned
    }

    override suspend fun deleteMapping(mappingId: String) {
        mappingDao.deleteMappingById(mappingId)
    }

    private suspend fun validateIntegrity(
        restaurantId: RestaurantId,
        supplierId: SupplierId,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?
    ) {
        val supplier = supplierDao.getById(supplierId.value) ?: throw ValidationError.SupplierNotFound
        if (supplier.restaurantId != restaurantId.value) throw ValidationError.SupplierOwnershipMismatch

        val ingredient = ingredientDao.getById(ingredientId.value) ?: throw ValidationError.IngredientNotFound
        if (ingredient.restaurantId != restaurantId.value) throw ValidationError.IngredientOwnershipMismatch

        if (unitOptionId != null) {
            val option = unitOptionDao.getById(unitOptionId.value) ?: throw ValidationError.UnitOptionNotFound
            if (option.ingredientId != ingredientId.value) throw ValidationError.InvalidPurchaseUnitOption
        }

        if (inventoryAreaId != null) {
            val area = areaDao.getById(inventoryAreaId.value) ?: throw ValidationError.RecordNotFound
            if (area.restaurantId != restaurantId.value) throw ValidationError.InvalidPurchaseArea
        }
    }
}
