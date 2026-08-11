package com.miara.cuentame.feature.ingredients.csvimport.domain

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.core.model.supplier.SupplierItemMapping
import com.miara.cuentame.core.ocr.parser.matching.InventoryNormalization
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_BASE_UNIT
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_CATEGORY
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_COUNT_UNIT
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_CURRENT_COST
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_DEFAULT_AREA
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_INGREDIENT_NAME
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_PACKAGE_CONVERSION
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_PURCHASE_PACKAGE
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_REORDER_POINT
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_SKU
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_SUPPLIER
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_VENDOR_CODE
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

class CsvImportService @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val categoryRepository: IngredientCategoryRepository,
    private val areaRepository: InventoryAreaRepository,
    private val supplierRepository: SupplierRepository,
    private val unitRepository: UnitRepository,
    private val mappingRepository: SupplierItemMappingRepository
) {
    suspend fun processCsv(
        restaurantId: RestaurantId,
        rawRows: List<Map<String, String>>
    ): CsvIngredientImportDocument {
        // 1. Batch load existing data including archived for conflicts
        val allIngredients = ingredientRepository.getIngredients(restaurantId, includeArchived = true)
        val allCategories = categoryRepository.getAllCategoriesForRestaurant(restaurantId)
        val activeAreas = areaRepository.observeActiveAreas().first()
        val allSuppliers = supplierRepository.observeSuppliers(restaurantId, includeArchived = true).first()
        val allMappings = mappingRepository.getAllMappings(restaurantId)
        val systemUnits = unitRepository.observeAll().first()

        val normalizedIngredientsMap = allIngredients.associateBy { it.normalizedName }
        val skuMap = allIngredients.filter { it.sku != null }.associateBy { it.sku!!.trim().lowercase() }
        
        val categoriesMap = allCategories.associateBy { it.name.normalizeName() }
        val areasMap = activeAreas.associateBy { it.name.normalizeName() }
        val suppliersMap = allSuppliers.associateBy { it.name.normalizeName() }
        
        val mappingsMap = allMappings.groupBy { "${it.supplierId.value}|${it.keyType}|${it.normalizedKey}" }
        
        // Ambiguous Unit Resolution: map normalized name/symbol to list of matching units
        val unitsLookup = systemUnits.flatMap { unit ->
            listOf(unit.symbol.normalizeName() to unit, unit.name.normalizeName() to unit)
        }.groupBy({ it.first }, { it.second })

        // Track duplicates within CSV
        val csvNormalizedNames = mutableSetOf<String>()
        val csvSkus = mutableSetOf<String>()
        val csvVendorCodes = mutableSetOf<String>() // format: "supplier_norm|vendor_code_norm"

        val processedRows = rawRows.mapIndexed { index, rawData ->
            val rowNumber = index + 2 // 1-based + header row
            validateRow(
                rowNumber = rowNumber,
                rawData = rawData,
                normalizedIngredientsMap = normalizedIngredientsMap,
                skuMap = skuMap,
                categoriesMap = categoriesMap,
                areasMap = areasMap,
                suppliersMap = suppliersMap,
                mappingsMap = mappingsMap,
                unitsLookup = unitsLookup,
                csvNormalizedNames = csvNormalizedNames,
                csvSkus = csvSkus,
                csvVendorCodes = csvVendorCodes
            )
        }

        return CsvIngredientImportDocument(processedRows)
    }

    private fun validateRow(
        rowNumber: Int,
        rawData: Map<String, String>,
        normalizedIngredientsMap: Map<String, com.miara.cuentame.core.model.ingredient.Ingredient>,
        skuMap: Map<String, com.miara.cuentame.core.model.ingredient.Ingredient>,
        categoriesMap: Map<String, com.miara.cuentame.core.model.ingredient.IngredientCategory>,
        areasMap: Map<String, com.miara.cuentame.core.model.inventory.InventoryArea>,
        suppliersMap: Map<String, com.miara.cuentame.core.model.supplier.Supplier>,
        mappingsMap: Map<String, List<SupplierItemMapping>>,
        unitsLookup: Map<String, List<UnitOfMeasure>>,
        csvNormalizedNames: MutableSet<String>,
        csvSkus: MutableSet<String>,
        csvVendorCodes: MutableSet<String>
    ): CsvIngredientImportRow {
        val issues = mutableListOf<CsvImportRowIssue>()
        
        val rawName = rawData[HEADER_INGREDIENT_NAME]?.trim() ?: ""
        val normalizedName = rawName.normalizeName()
        
        val rawSku = rawData[HEADER_SKU]?.trim()
        val normalizedSku = rawSku?.lowercase()
        
        val rawCategory = rawData[HEADER_CATEGORY]?.trim()
        val rawBaseUnit = rawData[HEADER_BASE_UNIT]?.trim() ?: ""
        val rawCountUnit = rawData[HEADER_COUNT_UNIT]?.trim()
        val rawPurchasePackage = rawData[HEADER_PURCHASE_PACKAGE]?.trim()
        val rawPackageConversion = rawData[HEADER_PACKAGE_CONVERSION]?.trim()
        val rawDefaultArea = rawData[HEADER_DEFAULT_AREA]?.trim()
        val rawSupplier = rawData[HEADER_SUPPLIER]?.trim()
        val rawVendorCode = rawData[HEADER_VENDOR_CODE]?.trim()
        val normalizedVendorCode = InventoryNormalization.normalizeVendorCode(rawVendorCode)
        
        val rawCurrentCost = rawData[HEADER_CURRENT_COST]?.trim()
        val rawReorderPoint = rawData[HEADER_REORDER_POINT]?.trim()

        // 1. Ingredient Name Validation
        if (rawName.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "Ingredient name is required", CsvImportIssueSeverity.ERROR))
        } else if (normalizedName.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, "Invalid ingredient name", CsvImportIssueSeverity.ERROR))
        } else {
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

        // 2. SKU Validation
        if (!normalizedSku.isNullOrBlank()) {
            if (!csvSkus.add(normalizedSku)) {
                issues.add(CsvImportRowIssue(HEADER_SKU, "Duplicate SKU in CSV", CsvImportIssueSeverity.ERROR))
            }
            val existing = skuMap[normalizedSku]
            if (existing != null) {
                issues.add(CsvImportRowIssue(HEADER_SKU, "An ingredient with this SKU already exists", CsvImportIssueSeverity.ERROR))
            }
        }

        // 3. Unit Resolution (Ambiguity Check)
        fun resolveUnit(raw: String?, field: String, required: Boolean): UnitOfMeasure? {
            if (raw.isNullOrBlank()) {
                if (required) issues.add(CsvImportRowIssue(field, "Unit is required", CsvImportIssueSeverity.ERROR))
                return null
            }
            val matches = unitsLookup[raw.normalizeName()] ?: emptyList()
            return when {
                matches.isEmpty() -> {
                    issues.add(CsvImportRowIssue(field, "Unknown unit: $raw", CsvImportIssueSeverity.ERROR))
                    null
                }
                matches.size > 1 -> {
                    val distinctIds = matches.map { it.id }.distinct()
                    if (distinctIds.size > 1) {
                        issues.add(CsvImportRowIssue(field, "Ambiguous unit: $raw (${matches.joinToString { it.symbol }})", CsvImportIssueSeverity.ERROR))
                        null
                    } else matches.first()
                }
                else -> matches.first()
            }
        }

        val baseUnit = resolveUnit(rawBaseUnit, HEADER_BASE_UNIT, true)
        val countUnit = resolveUnit(rawCountUnit, HEADER_COUNT_UNIT, false)

        if (baseUnit != null && countUnit != null) {
            if (baseUnit.dimension != countUnit.dimension) {
                issues.add(CsvImportRowIssue(HEADER_COUNT_UNIT, "Count unit dimension (${countUnit.dimension}) is incompatible with base unit (${baseUnit.dimension})", CsvImportIssueSeverity.ERROR))
            }
        }

        // 4. Category / Supplier / Area Resolution (Archived Record Policy)
        val category = if (!rawCategory.isNullOrBlank()) {
            val existing = categoriesMap[rawCategory.normalizeName()]
            when {
                existing == null -> {
                    issues.add(CsvImportRowIssue(HEADER_CATEGORY, "Category will be created: $rawCategory", CsvImportIssueSeverity.WARNING))
                    null
                }
                existing.deletedAt != null -> {
                    issues.add(CsvImportRowIssue(HEADER_CATEGORY, "Category is archived: $rawCategory", CsvImportIssueSeverity.ERROR))
                    null
                }
                !existing.isActive -> {
                     issues.add(CsvImportRowIssue(HEADER_CATEGORY, "Category is inactive: $rawCategory", CsvImportIssueSeverity.ERROR))
                     null
                }
                else -> existing
            }
        } else null

        val supplier = if (!rawSupplier.isNullOrBlank()) {
            val existing = suppliersMap[rawSupplier.normalizeName()]
            when {
                existing == null -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, "Supplier will be created: $rawSupplier", CsvImportIssueSeverity.WARNING))
                    null
                }
                existing.deletedAt != null -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, "Supplier is archived: $rawSupplier", CsvImportIssueSeverity.ERROR))
                    null
                }
                !existing.isActive -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, "Supplier is inactive: $rawSupplier", CsvImportIssueSeverity.ERROR))
                    null
                }
                else -> existing
            }
        } else null

        val area = if (!rawDefaultArea.isNullOrBlank()) {
            areasMap[rawDefaultArea.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_DEFAULT_AREA, "Unknown or inactive storage area: $rawDefaultArea", CsvImportIssueSeverity.ERROR))
                null
            }
        } else null

        // 5. Vendor Code Logic
        if (!normalizedVendorCode.isBlank()) {
            if (rawSupplier.isNullOrBlank()) {
                issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, "Vendor item code requires a supplier", CsvImportIssueSeverity.ERROR))
            } else {
                val supKey = rawSupplier.normalizeName()
                val conflictKey = "$supKey|$normalizedVendorCode"
                if (!csvVendorCodes.add(conflictKey)) {
                    issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, "Duplicate supplier + vendor code in CSV", CsvImportIssueSeverity.ERROR))
                }
                
                val existingSupplier = suppliersMap[supKey]
                if (existingSupplier != null) {
                    val mappingKey = "${existingSupplier.id.value}|${com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType.VENDOR_CODE}|$normalizedVendorCode"
                    val existingMappings = mappingsMap[mappingKey] ?: emptyList()
                    if (existingMappings.isNotEmpty()) {
                        issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, "Supplier mapping for '$normalizedVendorCode' already exists in database", CsvImportIssueSeverity.ERROR))
                    }
                }
            }
        }

        // 6. Numeric Validation
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
            sku = if (rawSku.isNullOrBlank()) null else rawSku.trim(),
            categoryName = if (rawCategory.isNullOrBlank()) null else rawCategory.trim(),
            resolvedCategoryId = category?.id,
            baseUnitName = rawBaseUnit.trim(),
            resolvedBaseUnitId = baseUnit?.id,
            countUnitName = if (rawCountUnit.isNullOrBlank()) null else rawCountUnit.trim(),
            resolvedCountUnitId = countUnit?.id,
            purchasePackageName = if (rawPurchasePackage.isNullOrBlank()) null else rawPurchasePackage.trim(),
            packageConversionFactor = packageConversion,
            defaultAreaName = if (rawDefaultArea.isNullOrBlank()) null else rawDefaultArea.trim(),
            resolvedDefaultAreaId = area?.id,
            supplierName = if (rawSupplier.isNullOrBlank()) null else rawSupplier.trim(),
            resolvedSupplierId = supplier?.id,
            vendorItemCode = if (rawVendorCode.isNullOrBlank()) null else rawVendorCode.trim(),
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
