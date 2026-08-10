package com.miara.cuentame.feature.ingredient.import.domain

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_BASE_UNIT
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_CATEGORY
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_COUNT_UNIT
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_CURRENT_COST
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_DEFAULT_AREA
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_INGREDIENT_NAME
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_PACKAGE_CONVERSION
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_PURCHASE_PACKAGE
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_REORDER_POINT
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_SKU
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_SUPPLIER
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser.Companion.HEADER_VENDOR_CODE
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

class CsvImportService @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val categoryRepository: IngredientCategoryRepository,
    private val areaRepository: InventoryAreaRepository,
    private val supplierRepository: SupplierRepository,
    private val unitRepository: UnitRepository
) {
    suspend fun processCsv(
        restaurantId: RestaurantId,
        rawRows: List<Map<String, String>>
    ): CsvIngredientImportDocument {
        // 1. Batch load existing data for performance
        val existingIngredients = ingredientRepository.getIngredients(restaurantId, includeArchived = true)
        val activeCategories = categoryRepository.observeActiveCategories().first()
        val activeAreas = areaRepository.observeActiveAreas().first()
        val activeSuppliers = supplierRepository.observeSuppliers(restaurantId, includeArchived = false).first()
        val systemUnits = unitRepository.observeAll().first()

        val normalizedIngredientsMap = existingIngredients.associateBy { it.normalizedName }
        val skuMap = existingIngredients.filter { it.sku != null }.associateBy { it.sku!! }
        
        val normalizedCategoriesMap = activeCategories.associateBy { it.name.normalizeName() }
        val normalizedAreasMap = activeAreas.associateBy { it.name.normalizeName() }
        val normalizedSuppliersMap = activeSuppliers.associateBy { it.name.normalizeName() }
        
        val unitsMap = systemUnits.flatMap { unit ->
            listOf(unit.symbol.normalizeName() to unit, unit.name.normalizeName() to unit)
        }.toMap()

        // Track duplicates within CSV
        val csvNormalizedNames = mutableSetOf<String>()
        val csvSkus = mutableSetOf<String>()

        val processedRows = rawRows.mapIndexed { index, rawData ->
            val rowNumber = index + 2 // 1-based + header row
            validateRow(
                rowNumber = rowNumber,
                rawData = rawData,
                normalizedIngredientsMap = normalizedIngredientsMap,
                skuMap = skuMap,
                normalizedCategoriesMap = normalizedCategoriesMap,
                normalizedAreasMap = normalizedAreasMap,
                normalizedSuppliersMap = normalizedSuppliersMap,
                unitsMap = unitsMap,
                csvNormalizedNames = csvNormalizedNames,
                csvSkus = csvSkus
            )
        }

        return CsvIngredientImportDocument(processedRows)
    }

    private fun validateRow(
        rowNumber: Int,
        rawData: Map<String, String>,
        normalizedIngredientsMap: Map<String, com.miara.cuentame.core.model.ingredient.Ingredient>,
        skuMap: Map<String, com.miara.cuentame.core.model.ingredient.Ingredient>,
        normalizedCategoriesMap: Map<String, com.miara.cuentame.core.model.ingredient.IngredientCategory>,
        normalizedAreasMap: Map<String, com.miara.cuentame.core.model.inventory.InventoryArea>,
        normalizedSuppliersMap: Map<String, com.miara.cuentame.core.model.supplier.Supplier>,
        unitsMap: Map<String, com.miara.cuentame.core.model.inventory.UnitOfMeasure>,
        csvNormalizedNames: MutableSet<String>,
        csvSkus: MutableSet<String>
    ): CsvIngredientImportRow {
        val issues = mutableListOf<CsvImportRowIssue>()
        
        val rawName = rawData[HEADER_INGREDIENT_NAME]?.trim() ?: ""
        val normalizedName = rawName.normalizeName()
        
        val rawSku = rawData[HEADER_SKU]?.trim()
        val rawCategory = rawData[HEADER_CATEGORY]?.trim()
        val rawBaseUnit = rawData[HEADER_BASE_UNIT]?.trim() ?: ""
        val rawCountUnit = rawData[HEADER_COUNT_UNIT]?.trim()
        val rawPurchasePackage = rawData[HEADER_PURCHASE_PACKAGE]?.trim()
        val rawPackageConversion = rawData[HEADER_PACKAGE_CONVERSION]?.trim()
        val rawDefaultArea = rawData[HEADER_DEFAULT_AREA]?.trim()
        val rawSupplier = rawData[HEADER_SUPPLIER]?.trim()
        val rawVendorCode = rawData[HEADER_VENDOR_CODE]?.trim()
        val rawCurrentCost = rawData[HEADER_CURRENT_COST]?.trim()
        val rawReorderPoint = rawData[HEADER_REORDER_POINT]?.trim()

        // Basic Validation
        if (rawName.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "Ingredient name is required", CsvImportIssueSeverity.ERROR))
        } else if (normalizedName.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "Invalid ingredient name", CsvImportIssueSeverity.ERROR))
        }

        if (rawBaseUnit.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_BASE_UNIT, "Base unit is required", CsvImportIssueSeverity.ERROR))
        }

        // Duplicate detection
        if (normalizedName.isNotBlank()) {
            if (!csvNormalizedNames.add(normalizedName)) {
                issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "Duplicate ingredient name in CSV", CsvImportIssueSeverity.ERROR))
            }
            val existing = normalizedIngredientsMap[normalizedName]
            if (existing != null) {
                if (existing.deletedAt != null) {
                    issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "An archived ingredient with this name already exists", CsvImportIssueSeverity.ERROR))
                } else {
                    issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "An active ingredient with this name already exists", CsvImportIssueSeverity.ERROR))
                }
            }
        }

        if (!rawSku.isNullOrBlank()) {
            if (!csvSkus.add(rawSku)) {
                issues.add(CsvImportRowIssue(HEADER_SKU, "Duplicate SKU in CSV", CsvImportIssueSeverity.ERROR))
            }
            val existing = skuMap[rawSku]
            if (existing != null) {
                issues.add(CsvImportRowIssue(HEADER_SKU, "An ingredient with this SKU already exists", CsvImportIssueSeverity.ERROR))
            }
        }

        // Resolution
        val baseUnit = if (rawBaseUnit.isNotBlank()) {
            unitsMap[rawBaseUnit.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_BASE_UNIT, "Unknown unit: $rawBaseUnit", CsvImportIssueSeverity.ERROR))
                null
            }
        } else null

        val countUnit = if (!rawCountUnit.isNullOrBlank()) {
            unitsMap[rawCountUnit.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_COUNT_UNIT, "Unknown unit: $rawCountUnit", CsvImportIssueSeverity.ERROR))
                null
            }
        } else null

        if (baseUnit != null && countUnit != null) {
            if (baseUnit.dimension != countUnit.dimension) {
                issues.add(CsvImportRowIssue(HEADER_COUNT_UNIT, "Count unit dimension (${countUnit.dimension}) is incompatible with base unit (${baseUnit.dimension})", CsvImportIssueSeverity.ERROR))
            }
        }

        val category = if (!rawCategory.isNullOrBlank()) {
            normalizedCategoriesMap[rawCategory.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_CATEGORY, "Category will be created: $rawCategory", CsvImportIssueSeverity.WARNING))
                null
            }
        } else null

        val area = if (!rawDefaultArea.isNullOrBlank()) {
            normalizedAreasMap[rawDefaultArea.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_DEFAULT_AREA, "Unknown storage area: $rawDefaultArea", CsvImportIssueSeverity.ERROR))
                null
            }
        } else null

        val supplier = if (!rawSupplier.isNullOrBlank()) {
            normalizedSuppliersMap[rawSupplier.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_SUPPLIER, "Supplier will be created: $rawSupplier", CsvImportIssueSeverity.WARNING))
                null
            }
        } else null

        if (!rawVendorCode.isNullOrBlank() && rawSupplier.isNullOrBlank()) {
            issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, "Vendor item code requires a supplier", CsvImportIssueSeverity.ERROR))
        }

        // Numeric Validation
        val packageConversion = if (!rawPackageConversion.isNullOrBlank()) {
            val value = rawPackageConversion.toBigDecimalOrNull()
            if (value == null || value <= BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_PACKAGE_CONVERSION, "Package conversion factor must be a positive number", CsvImportIssueSeverity.ERROR))
                null
            } else value
        } else null

        if (!rawPurchasePackage.isNullOrBlank() && packageConversion == null) {
            issues.add(CsvImportRowIssue(HEADER_PACKAGE_CONVERSION, "Package conversion factor is required when purchase package is specified", CsvImportIssueSeverity.ERROR))
        }

        val currentCost = if (!rawCurrentCost.isNullOrBlank()) {
            val value = rawCurrentCost.toBigDecimalOrNull()
            if (value == null || value < BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_CURRENT_COST, "Current cost must be a non-negative number", CsvImportIssueSeverity.ERROR))
                null
            } else value
        } else null

        val reorderPoint = if (!rawReorderPoint.isNullOrBlank()) {
            val value = rawReorderPoint.toBigDecimalOrNull()
            if (value == null || value < BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_REORDER_POINT, "Reorder point must be a non-negative number", CsvImportIssueSeverity.ERROR))
                null
            } else value
        } else null

        val normalizedData = NormalizedIngredientData(
            name = rawName,
            sku = if (rawSku.isNullOrBlank()) null else rawSku,
            categoryName = if (rawCategory.isNullOrBlank()) null else rawCategory,
            resolvedCategoryId = category?.id,
            baseUnitName = rawBaseUnit,
            resolvedBaseUnitId = baseUnit?.id,
            countUnitName = if (rawCountUnit.isNullOrBlank()) null else rawCountUnit,
            resolvedCountUnitId = countUnit?.id,
            purchasePackageName = if (rawPurchasePackage.isNullOrBlank()) null else rawPurchasePackage,
            packageConversionFactor = packageConversion,
            defaultAreaName = if (rawDefaultArea.isNullOrBlank()) null else rawDefaultArea,
            resolvedDefaultAreaId = area?.id,
            supplierName = if (rawSupplier.isNullOrBlank()) null else rawSupplier,
            resolvedSupplierId = supplier?.id,
            vendorItemCode = if (rawVendorCode.isNullOrBlank()) null else rawVendorCode,
            currentCostPerBaseUnit = currentCost,
            reorderPointBase = reorderPoint
        )

        val hasErrors = issues.any { it.severity == CsvImportIssueSeverity.ERROR }
        val hasWarnings = issues.any { it.severity == CsvImportIssueSeverity.WARNING }
        val status = when {
            hasErrors -> CsvImportRowStatus.ERROR
            hasWarnings -> CsvImportRowStatus.WARNING
            else -> CsvImportRowStatus.READY
        }

        return CsvIngredientImportRow(
            rowNumber = rowNumber,
            rawData = rawData,
            normalizedData = normalizedData,
            issues = issues,
            status = status
        )
    }
}
