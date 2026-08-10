package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.SupplierItemMappingDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.SupplierEntity
import com.miara.cuentame.core.database.entity.SupplierItemMappingEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.repository.CsvImportRepository
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.feature.ingredient.import.domain.CsvIngredientImportDocument
import com.miara.cuentame.feature.ingredient.import.domain.CsvImportRowStatus
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomCsvImportRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val categoryDao: IngredientCategoryDao,
    private val supplierDao: SupplierDao,
    private val mappingDao: SupplierItemMappingDao,
    private val costDao: IngredientCostProjectionDao,
    private val unitDao: UnitDao,
    private val converter: StandardUnitConverter,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : CsvImportRepository {

    override suspend fun commitImport(
        restaurantId: RestaurantId,
        document: CsvIngredientImportDocument
    ): ImportResult {
        return try {
            database.withTransaction {
                val now = timeProvider.now()
                val includedRows = document.rows.filter { it.isIncluded && it.status != CsvImportRowStatus.ERROR && it.status != CsvImportRowStatus.SKIPPED }
                
                if (includedRows.isEmpty()) {
                    return@withTransaction ImportResult.Success(0, 0, 0, 0, document.rows.count { !it.isIncluded || it.status == CsvImportRowStatus.SKIPPED })
                }

                // 1. Re-validate critical state (TOCTOU)
                val existingIngredients = ingredientDao.getActiveIngredients(restaurantId.value)
                val includedNormalizedNames = includedRows.mapNotNull { it.normalizedData?.name?.normalizeName() }
                if (existingIngredients.any { it.normalizedName in includedNormalizedNames }) {
                    return@withTransaction ImportResult.StaleData
                }

                // 2. Resolve or Create Categories
                val categoryCache = mutableMapOf<String, IngredientCategoryId>()
                var categoriesCreated = 0
                includedRows.forEach { row ->
                    val catName = row.normalizedData?.categoryName
                    if (!catName.isNullOrBlank()) {
                        val norm = catName.normalizeName()
                        if (!categoryCache.containsKey(norm)) {
                            val existing = categoryDao.findByNormalizedName(restaurantId.value, norm)
                            if (existing != null) {
                                categoryCache[norm] = IngredientCategoryId(existing.id)
                            } else {
                                val newId = IngredientCategoryId(idGenerator.newId())
                                categoryDao.upsert(
                                    IngredientCategoryEntity(
                                        id = newId.value,
                                        restaurantId = restaurantId.value,
                                        name = catName,
                                        normalizedName = norm,
                                        sortOrder = 0,
                                        isActive = true,
                                        createdAt = now.toEpochMilli(),
                                        updatedAt = now.toEpochMilli(),
                                        deletedAt = null
                                    )
                                )
                                categoryCache[norm] = newId
                                categoriesCreated++
                            }
                        }
                    }
                }

                // 3. Resolve or Create Suppliers
                val supplierCache = mutableMapOf<String, SupplierId>()
                var suppliersCreated = 0
                includedRows.forEach { row ->
                    val supName = row.normalizedData?.supplierName
                    if (!supName.isNullOrBlank()) {
                        val norm = supName.normalizeName()
                        if (!supplierCache.containsKey(norm)) {
                            val existing = supplierDao.findByNormalizedName(restaurantId.value, norm)
                            if (existing != null) {
                                supplierCache[norm] = SupplierId(existing.id)
                            } else {
                                val newId = SupplierId(idGenerator.newId())
                                supplierDao.insert(
                                    SupplierEntity(
                                        id = newId.value,
                                        restaurantId = restaurantId.value,
                                        name = supName,
                                        normalizedName = norm,
                                        phone = null,
                                        email = null,
                                        notes = null,
                                        isActive = true,
                                        createdAt = now.toEpochMilli(),
                                        updatedAt = now.toEpochMilli(),
                                        deletedAt = null
                                    )
                                )
                                supplierCache[norm] = newId
                                suppliersCreated++
                            }
                        }
                    }
                }

                // 4. Create Ingredients, Options, Mappings, Costs
                var ingredientsCreated = 0
                var mappingsCreated = 0
                
                includedRows.forEach { row ->
                    val data = row.normalizedData ?: return@forEach
                    val ingredientId = IngredientId(idGenerator.newId())
                    val baseUnitId = data.resolvedBaseUnitId ?: return@forEach
                    
                    val ingredient = IngredientEntity(
                        id = ingredientId.value,
                        restaurantId = restaurantId.value,
                        name = data.name,
                        normalizedName = data.name.normalizeName(),
                        categoryId = data.resolvedCategoryId?.value ?: data.categoryName?.let { categoryCache[it.normalizeName()]?.value },
                        baseUnitId = baseUnitId.value,
                        defaultAreaId = data.resolvedDefaultAreaId?.value,
                        sku = data.sku,
                        notes = null,
                        reorderPointBase = data.reorderPointBase,
                        isActive = true,
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli(),
                        deletedAt = null
                    )
                    ingredientDao.insert(ingredient)
                    ingredientsCreated++

                    // Base Option
                    val baseUnit = unitDao.getById(baseUnitId.value)?.toDomain() ?: throw IllegalStateException("Base unit not found")
                    val baseOptionId = IngredientUnitOptionId(idGenerator.newId())
                    val baseOption = IngredientUnitOptionEntity(
                        id = baseOptionId.value,
                        ingredientId = ingredientId.value,
                        displayName = baseUnit.symbol,
                        shortLabel = baseUnit.symbol,
                        standardUnitId = baseUnit.id.value,
                        factorToBase = BigDecimal.ONE,
                        isBase = true,
                        isDefaultCount = data.countUnitName == null || data.countUnitName.normalizeName() == baseUnit.symbol.normalizeName() || data.countUnitName.normalizeName() == baseUnit.name.normalizeName(),
                        isDefaultPurchase = data.purchasePackageName == null,
                        isActive = true,
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli(),
                        deletedAt = null
                    )
                    unitOptionDao.insert(baseOption)

                    var currentDefaultPurchaseOptionId = if (baseOption.isDefaultPurchase) baseOption.id else null

                    // Count Option (if different from base)
                    if (data.resolvedCountUnitId != null && data.resolvedCountUnitId != baseUnitId) {
                        val countUnit = unitDao.getById(data.resolvedCountUnitId.value)?.toDomain() ?: throw IllegalStateException("Count unit not found")
                        val factor = converter.convert(BigDecimal.ONE, countUnit, baseUnit)
                        val countOptionId = IngredientUnitOptionId(idGenerator.newId())
                        unitOptionDao.insert(
                            IngredientUnitOptionEntity(
                                id = countOptionId.value,
                                ingredientId = ingredientId.value,
                                displayName = countUnit.symbol,
                                shortLabel = countUnit.symbol,
                                standardUnitId = countUnit.id.value,
                                factorToBase = factor,
                                isBase = false,
                                isDefaultCount = true,
                                isDefaultPurchase = false,
                                isActive = true,
                                createdAt = now.toEpochMilli(),
                                updatedAt = now.toEpochMilli(),
                                deletedAt = null
                            )
                        )
                    }

                    // Purchase Package Option
                    if (!data.purchasePackageName.isNullOrBlank() && data.packageConversionFactor != null) {
                        val packageOptionId = IngredientUnitOptionId(idGenerator.newId())
                        unitOptionDao.insert(
                            IngredientUnitOptionEntity(
                                id = packageOptionId.value,
                                ingredientId = ingredientId.value,
                                displayName = data.purchasePackageName,
                                shortLabel = data.purchasePackageName,
                                standardUnitId = null,
                                factorToBase = data.packageConversionFactor,
                                isBase = false,
                                isDefaultCount = false,
                                isDefaultPurchase = true,
                                isActive = true,
                                createdAt = now.toEpochMilli(),
                                updatedAt = now.toEpochMilli(),
                                deletedAt = null
                            )
                        )
                        currentDefaultPurchaseOptionId = packageOptionId.value
                    }

                    // Supplier Mapping
                    val supplierId = data.resolvedSupplierId ?: data.supplierName?.let { supplierCache[it.normalizeName()] }
                    if (supplierId != null && !data.vendorItemCode.isNullOrBlank()) {
                        mappingDao.insertMapping(
                            SupplierItemMappingEntity(
                                id = idGenerator.newId(),
                                restaurantId = restaurantId.value,
                                supplierId = supplierId.value,
                                keyType = SupplierItemMappingKeyType.VENDOR_CODE,
                                normalizedKey = data.vendorItemCode.normalizeName(),
                                sourceVendorCode = data.vendorItemCode,
                                sourceDescription = null,
                                sourcePackageText = data.purchasePackageName,
                                ingredientId = ingredientId.value,
                                unitOptionId = currentDefaultPurchaseOptionId,
                                inventoryAreaId = data.resolvedDefaultAreaId?.value,
                                createdAt = now.toEpochMilli(),
                                updatedAt = now.toEpochMilli(),
                                lastConfirmedAt = now.toEpochMilli()
                            )
                        )
                        mappingsCreated++
                    }

                    // Cost Projection
                    if (data.currentCostPerBaseUnit != null) {
                        costDao.upsert(
                            IngredientCostProjectionEntity(
                                restaurantId = restaurantId.value,
                                ingredientId = ingredientId.value,
                                averageUnitCostBase = data.currentCostPerBaseUnit.toString(),
                                updatedAt = now.toEpochMilli()
                            )
                        )
                    }
                }

                ImportResult.Success(
                    ingredientsCreated = ingredientsCreated,
                    categoriesCreated = categoriesCreated,
                    suppliersCreated = suppliersCreated,
                    mappingsCreated = mappingsCreated,
                    rowsSkipped = document.rows.size - includedRows.size
                )
            }
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Unknown error during import")
        }
    }
}
