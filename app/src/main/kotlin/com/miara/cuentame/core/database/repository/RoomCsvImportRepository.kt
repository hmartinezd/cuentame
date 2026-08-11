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
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.RestaurantDao
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
import com.miara.cuentame.core.domain.repository.ImportFailure
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.ocr.parser.matching.InventoryNormalization
import com.miara.cuentame.core.domain.repository.CsvIngredientImportDocument
import com.miara.cuentame.core.domain.repository.CsvImportRowStatus
import java.math.BigDecimal
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
    private val restaurantDao: RestaurantDao,
    private val areaDao: InventoryAreaDao,
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
                val includedRows = document.rows.filter { it.isIncluded }
                
                if (includedRows.isEmpty()) {
                    return@withTransaction ImportResult.Success(0, 0, 0, 0, document.rows.size)
                }

                // Require every included row to be valid according to the plan
                if (includedRows.any { it.status == CsvImportRowStatus.ERROR || it.normalizedData == null }) {
                    return@withTransaction ImportResult.Failure(ImportFailure.InvalidPlan)
                }

                val planData = includedRows.map { it.normalizedData!! }
                if (planData.any { data ->
                        data.name.normalizeName().isBlank() || data.resolvedBaseUnitId == null ||
                            (!data.countUnitName.isNullOrBlank() && data.resolvedCountUnitId == null) ||
                            (data.purchasePackageName.isNullOrBlank() != (data.packageConversionFactor == null)) ||
                            (!data.vendorItemCode.isNullOrBlank() && data.resolvedSupplierId == null && data.supplierName.isNullOrBlank()) ||
                            data.currentCostPerBaseUnit?.let { it < BigDecimal.ZERO } == true ||
                            data.reorderPointBase?.let { it < BigDecimal.ZERO } == true ||
                            (!data.purchasePackageName.isNullOrBlank() && (data.packageConversionFactor == null || data.packageConversionFactor <= BigDecimal.ZERO))
                    } || planData.map { it.name.normalizeName() }.toSet().size != planData.size ||
                    planData.mapNotNull { it.sku?.trim()?.lowercase()?.takeIf(String::isNotBlank) }.let { it.size != it.toSet().size } ||
                    planData.mapNotNull { data ->
                        val supplierKey = data.resolvedSupplierId?.value ?: data.supplierName?.normalizeName()
                        val vendorKey = InventoryNormalization.normalizeVendorCode(data.vendorItemCode)
                        if (supplierKey != null && vendorKey.isNotBlank()) "$supplierKey|$vendorKey" else null
                    }.let { it.size != it.toSet().size } || planData.any { data ->
                        listOfNotNull(
                            data.baseUnitName.takeIf(String::isNotBlank),
                            data.countUnitName?.takeIf { it.isNotBlank() && data.resolvedCountUnitId != data.resolvedBaseUnitId },
                            data.purchasePackageName?.takeIf(String::isNotBlank)
                        ).map { it.normalizeName() }.let { names -> names.size != names.toSet().size }
                    }) {
                    return@withTransaction ImportResult.Failure(ImportFailure.InvalidPlan)
                }

                // 1. Re-validate critical state (TOCTOU)
                val restaurant = restaurantDao.getById(restaurantId.value)
                if (restaurant == null || restaurant.deletedAt != null) {
                    return@withTransaction ImportResult.Failure(ImportFailure.RestaurantUnavailable)
                }

                // Load all relevant state for re-validation in bulk
                val existingIngredients = ingredientDao.getAllIngredients(restaurantId.value)
                val allCategories = categoryDao.getAllCategoriesForRestaurant(restaurantId.value)
                val activeAreas = areaDao.getActiveAreasSync(restaurantId.value)
                val allSuppliers = supplierDao.getAllSuppliersSync(restaurantId.value)
                val allMappings = mappingDao.getAllMappingsSync(restaurantId.value)
                val systemUnits = unitDao.getAllSync()

                val normIngMap = existingIngredients.associateBy { it.normalizedName }
                val skuMap = existingIngredients.filter { it.sku != null }.associateBy { it.sku!!.trim().lowercase() }
                val catMap = allCategories.associateBy { it.id }
                val catNormMap = allCategories.associateBy { it.normalizedName }
                val areaMap = activeAreas.associateBy { it.id }
                val supMap = allSuppliers.associateBy { it.id }
                val supNormMap = allSuppliers.associateBy { it.normalizedName }
                val mapLookup = allMappings.associateBy { "${it.supplierId}|${it.keyType}|${it.normalizedKey}" }
                val unitLookup = systemUnits.associateBy { it.id }

                // Check for conflicts
                for (row in includedRows) {
                    val data = row.normalizedData ?: return@withTransaction ImportResult.Failure(ImportFailure.InvalidPlan)
                    
                    // Name/SKU conflicts
                    if (normIngMap.containsKey(data.name.normalizeName())) {
                        return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }
                    if (data.sku != null && skuMap.containsKey(data.sku.trim().lowercase())) {
                        return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }

                    // Unit validation
                    val baseUnit = unitLookup[data.resolvedBaseUnitId?.value] ?: return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    if (!baseUnit.isSystem) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    
                    if (data.resolvedCountUnitId != null) {
                        val countUnit = unitLookup[data.resolvedCountUnitId.value] ?: return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                        if (countUnit.dimension != baseUnit.dimension) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }

                    // Category validation
                    if (data.resolvedCategoryId != null) {
                        val cat = catMap[data.resolvedCategoryId.value] ?: return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                        if (!cat.isActive || cat.deletedAt != null || cat.restaurantId != restaurantId.value) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    } else if (!data.categoryName.isNullOrBlank()) {
                        if (catNormMap.containsKey(data.categoryName.normalizeName())) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }

                    // Supplier validation
                    if (data.resolvedSupplierId != null) {
                        val sup = supMap[data.resolvedSupplierId.value] ?: return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                        if (!sup.isActive || sup.deletedAt != null || sup.restaurantId != restaurantId.value) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    } else if (!data.supplierName.isNullOrBlank()) {
                        if (supNormMap.containsKey(data.supplierName.normalizeName())) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }

                    // Mapping validation
                    if (!data.vendorItemCode.isNullOrBlank()) {
                        val supId = data.resolvedSupplierId?.value ?: data.supplierName?.let { supNormMap[it.normalizeName()]?.id }
                        if (supId != null) {
                            val mappingKey = "$supId|${SupplierItemMappingKeyType.VENDOR_CODE}|${InventoryNormalization.normalizeVendorCode(data.vendorItemCode)}"
                            if (mapLookup.containsKey(mappingKey)) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                        }
                    }

                    // Area validation
                    if (data.resolvedDefaultAreaId != null) {
                        val area = areaMap[data.resolvedDefaultAreaId.value] ?: return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                        if (area.restaurantId != restaurantId.value) return@withTransaction ImportResult.Failure(ImportFailure.StateChanged)
                    }
                }

                // 2. Resolve or Create Categories
                val categoryCache = includedRows.mapNotNull { row ->
                    val data = row.normalizedData!!
                    val name = data.categoryName ?: return@mapNotNull null
                    val id = data.resolvedCategoryId ?: return@mapNotNull null
                    name.normalizeName() to id
                }.toMap().toMutableMap()
                var categoriesCreated = 0
                includedRows.forEach { row ->
                    val catName = row.normalizedData?.categoryName
                    if (!catName.isNullOrBlank()) {
                        val norm = catName.normalizeName()
                        if (!categoryCache.containsKey(norm)) {
                            // Already checked in re-validation
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

                // 3. Resolve or Create Suppliers
                val supplierCache = includedRows.mapNotNull { row ->
                    val data = row.normalizedData!!
                    val name = data.supplierName ?: return@mapNotNull null
                    val id = data.resolvedSupplierId ?: return@mapNotNull null
                    name.normalizeName() to id
                }.toMap().toMutableMap()
                var suppliersCreated = 0
                includedRows.forEach { row ->
                    val supName = row.normalizedData?.supplierName
                    if (!supName.isNullOrBlank()) {
                        val norm = supName.normalizeName()
                        if (!supplierCache.containsKey(norm)) {
                            // Already checked in re-validation
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

                // 4. Create Ingredients, Options, Mappings, Costs
                var ingredientsCreated = 0
                var mappingsCreated = 0
                
                includedRows.forEach { row ->
                    val data = row.normalizedData!!
                    val ingredientId = IngredientId(idGenerator.newId())
                    val baseUnitId = data.resolvedBaseUnitId!!
                    
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
                    val baseUnitEntity = unitDao.getById(baseUnitId.value) ?: throw IllegalStateException("Base unit not found")
                    val baseUnitDomain = baseUnitEntity.toDomain()
                    val baseOptionId = IngredientUnitOptionId(idGenerator.newId())
                    
                    val isBaseDefaultCount = data.countUnitName == null || data.countUnitName.normalizeName() == baseUnitDomain.symbol.normalizeName() || data.countUnitName.normalizeName() == baseUnitDomain.name.normalizeName()
                    val isBaseDefaultPurchase = data.purchasePackageName == null

                    val baseOption = IngredientUnitOptionEntity(
                        id = baseOptionId.value,
                        ingredientId = ingredientId.value,
                        displayName = baseUnitDomain.symbol,
                        shortLabel = baseUnitDomain.symbol,
                        standardUnitId = baseUnitDomain.id.value,
                        factorToBase = BigDecimal.ONE,
                        isBase = true,
                        isDefaultCount = isBaseDefaultCount,
                        isDefaultPurchase = isBaseDefaultPurchase,
                        isActive = true,
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli(),
                        deletedAt = null
                    )
                    unitOptionDao.insert(baseOption)

                    var currentDefaultPurchaseOptionId = if (isBaseDefaultPurchase) baseOption.id else null

                    // Count Option (if different from base)
                    if (data.resolvedCountUnitId != null && data.resolvedCountUnitId != baseUnitId) {
                        val countUnitEntity = unitDao.getById(data.resolvedCountUnitId.value) ?: throw IllegalStateException("Count unit not found")
                        val countUnitDomain = countUnitEntity.toDomain()
                        val factor = converter.convert(BigDecimal.ONE, countUnitDomain, baseUnitDomain)
                        val countOptionId = IngredientUnitOptionId(idGenerator.newId())
                        unitOptionDao.insert(
                            IngredientUnitOptionEntity(
                                id = countOptionId.value,
                                ingredientId = ingredientId.value,
                                displayName = countUnitDomain.symbol,
                                shortLabel = countUnitDomain.symbol,
                                standardUnitId = countUnitDomain.id.value,
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
                        // Already checked in re-validation that it doesn't exist
                        mappingDao.insertMappingStrict(
                            SupplierItemMappingEntity(
                                id = idGenerator.newId(),
                                restaurantId = restaurantId.value,
                                supplierId = supplierId.value,
                                keyType = SupplierItemMappingKeyType.VENDOR_CODE,
                                normalizedKey = InventoryNormalization.normalizeVendorCode(data.vendorItemCode),
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            ImportResult.Failure(ImportFailure.PersistenceFailure)
        }
    }
}
