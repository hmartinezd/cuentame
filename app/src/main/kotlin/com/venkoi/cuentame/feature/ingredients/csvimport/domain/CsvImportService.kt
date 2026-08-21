package com.venkoi.cuentame.feature.ingredients.csvimport.domain

import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.text.normalizeName
import com.venkoi.cuentame.core.domain.repository.IngredientCategoryRepository
import com.venkoi.cuentame.core.domain.repository.IngredientRepository
import com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository
import com.venkoi.cuentame.core.domain.repository.SupplierRepository
import com.venkoi.cuentame.core.domain.repository.UnitRepository
import com.venkoi.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import com.venkoi.cuentame.core.model.supplier.SupplierItemMapping
import com.venkoi.cuentame.core.ocr.parser.matching.InventoryNormalization
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_BASE_UNIT
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_CATEGORY
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_COUNT_UNIT
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_CURRENT_COST
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_DEFAULT_AREA
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_INGREDIENT_NAME
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_PACKAGE_CONVERSION
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_PURCHASE_PACKAGE
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_REORDER_POINT
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_SKU
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_SUPPLIER
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser.Companion.HEADER_VENDOR_CODE
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
        
        val categoriesMap = allCategories.groupBy { it.name.normalizeName() }
        val areasMap = activeAreas.associateBy { it.name.normalizeName() }
        val suppliersMap = allSuppliers.groupBy { it.name.normalizeName() }
        
        val mappingsMap = allMappings.groupBy { "${it.supplierId.value}|${it.keyType}|${it.normalizedKey}" }
        
        // Ambiguous Unit Resolution: map normalized name/symbol to list of matching units
        val unitsLookup = systemUnits.flatMap { unit ->
            (listOf(unit.symbol, unit.name) + UnitImportAliases.aliasesFor(unit.id.value)).map { it.normalizeName() to unit }
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
        normalizedIngredientsMap: Map<String, com.venkoi.cuentame.core.model.ingredient.Ingredient>,
        skuMap: Map<String, com.venkoi.cuentame.core.model.ingredient.Ingredient>,
        categoriesMap: Map<String, List<com.venkoi.cuentame.core.model.ingredient.IngredientCategory>>,
        areasMap: Map<String, com.venkoi.cuentame.core.model.inventory.InventoryArea>,
        suppliersMap: Map<String, List<com.venkoi.cuentame.core.model.supplier.Supplier>>,
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
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, CsvImportIssueCode.INGREDIENT_NAME_REQUIRED, CsvImportIssueSeverity.ERROR))
        } else if (normalizedName.isBlank()) {
            issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, CsvImportIssueCode.INVALID_INGREDIENT_NAME, CsvImportIssueSeverity.ERROR))
        } else {
            if (!csvNormalizedNames.add(normalizedName)) {
                issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, CsvImportIssueCode.DUPLICATE_INGREDIENT_NAME_IN_FILE, CsvImportIssueSeverity.ERROR))
            }
            val existing = normalizedIngredientsMap[normalizedName]
            if (existing != null) {
                if (existing.deletedAt != null) {
                    issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, CsvImportIssueCode.EXISTING_ARCHIVED_INGREDIENT, CsvImportIssueSeverity.ERROR))
                } else {
                    issues.add(CsvImportRowIssue(HEADER_INGREDIENT_NAME, CsvImportIssueCode.EXISTING_ACTIVE_INGREDIENT, CsvImportIssueSeverity.ERROR))
                }
            }
        }

        // 2. SKU Validation
        if (!normalizedSku.isNullOrBlank()) {
            if (!csvSkus.add(normalizedSku)) {
                issues.add(CsvImportRowIssue(HEADER_SKU, CsvImportIssueCode.DUPLICATE_SKU_IN_FILE, CsvImportIssueSeverity.ERROR))
            }
            val existing = skuMap[normalizedSku]
            if (existing != null) {
                issues.add(CsvImportRowIssue(HEADER_SKU, CsvImportIssueCode.EXISTING_SKU, CsvImportIssueSeverity.ERROR))
            }
        }

        // 3. Unit Resolution (Ambiguity Check)
        fun resolveUnit(raw: String?, field: String, required: Boolean): UnitOfMeasure? {
            if (raw.isNullOrBlank()) {
                if (required) issues.add(CsvImportRowIssue(field, CsvImportIssueCode.UNIT_REQUIRED, CsvImportIssueSeverity.ERROR))
                return null
            }
            val matches = unitsLookup[raw.normalizeName()] ?: emptyList()
            return when {
                matches.isEmpty() -> {
                    issues.add(CsvImportRowIssue(field, CsvImportIssueCode.UNKNOWN_UNIT, CsvImportIssueSeverity.ERROR, listOf(raw)))
                    null
                }
                matches.size > 1 -> {
                    val distinctIds = matches.map { it.id }.distinct()
                    if (distinctIds.size > 1) {
                        issues.add(CsvImportRowIssue(field, CsvImportIssueCode.AMBIGUOUS_UNIT, CsvImportIssueSeverity.ERROR, listOf(raw, matches.distinctBy { it.id }.joinToString { it.symbol })))
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
                issues.add(CsvImportRowIssue(HEADER_COUNT_UNIT, CsvImportIssueCode.INCOMPATIBLE_COUNT_UNIT, CsvImportIssueSeverity.ERROR, listOf(countUnit.symbol, baseUnit.symbol)))
            }
        }

        // 4. Category / Supplier / Area Resolution (Archived Record Policy)
        val category = if (!rawCategory.isNullOrBlank()) {
            val matches = categoriesMap[rawCategory.normalizeName()].orEmpty()
            val active = matches.filter { it.deletedAt == null && it.isActive }
            val inactive = matches.filter { it.deletedAt == null && !it.isActive }
            when {
                matches.isEmpty() -> {
                    issues.add(CsvImportRowIssue(HEADER_CATEGORY, CsvImportIssueCode.CATEGORY_WILL_BE_CREATED, CsvImportIssueSeverity.WARNING, listOf(rawCategory)))
                    null
                }
                active.size > 1 -> {
                    issues.add(CsvImportRowIssue(HEADER_CATEGORY, CsvImportIssueCode.CATEGORY_DATA_CONFLICT, CsvImportIssueSeverity.ERROR, listOf(rawCategory)))
                    null
                }
                active.size == 1 -> active.single()
                inactive.isNotEmpty() -> {
                     issues.add(CsvImportRowIssue(HEADER_CATEGORY, CsvImportIssueCode.CATEGORY_INACTIVE, CsvImportIssueSeverity.ERROR, listOf(rawCategory)))
                     null
                }
                else -> {
                    issues.add(CsvImportRowIssue(HEADER_CATEGORY, CsvImportIssueCode.CATEGORY_ARCHIVED, CsvImportIssueSeverity.ERROR, listOf(rawCategory)))
                    null
                }
            }
        } else null

        val supplier = if (!rawSupplier.isNullOrBlank()) {
            val matches = suppliersMap[rawSupplier.normalizeName()].orEmpty()
            val active = matches.filter { it.deletedAt == null && it.isActive }
            val inactive = matches.filter { it.deletedAt == null && !it.isActive }
            when {
                matches.isEmpty() -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, CsvImportIssueCode.SUPPLIER_WILL_BE_CREATED, CsvImportIssueSeverity.WARNING, listOf(rawSupplier)))
                    null
                }
                active.size > 1 -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, CsvImportIssueCode.SUPPLIER_DATA_CONFLICT, CsvImportIssueSeverity.ERROR, listOf(rawSupplier)))
                    null
                }
                active.size == 1 -> active.single()
                inactive.isNotEmpty() -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, CsvImportIssueCode.SUPPLIER_INACTIVE, CsvImportIssueSeverity.ERROR, listOf(rawSupplier)))
                    null
                }
                else -> {
                    issues.add(CsvImportRowIssue(HEADER_SUPPLIER, CsvImportIssueCode.SUPPLIER_ARCHIVED, CsvImportIssueSeverity.ERROR, listOf(rawSupplier)))
                    null
                }
            }
        } else null

        val area = if (!rawDefaultArea.isNullOrBlank()) {
            areasMap[rawDefaultArea.normalizeName()] ?: run {
                issues.add(CsvImportRowIssue(HEADER_DEFAULT_AREA, CsvImportIssueCode.UNKNOWN_AREA, CsvImportIssueSeverity.ERROR, listOf(rawDefaultArea)))
                null
            }
        } else null

        // 5. Vendor Code Logic
        if (!normalizedVendorCode.isBlank()) {
            if (rawSupplier.isNullOrBlank()) {
                issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, CsvImportIssueCode.VENDOR_CODE_REQUIRES_SUPPLIER, CsvImportIssueSeverity.ERROR))
            } else {
                val supKey = rawSupplier.normalizeName()
                val conflictKey = "$supKey|$normalizedVendorCode"
                if (!csvVendorCodes.add(conflictKey)) {
                    issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, CsvImportIssueCode.DUPLICATE_VENDOR_CODE, CsvImportIssueSeverity.ERROR))
                }
                
                val existingSupplier = suppliersMap[supKey].orEmpty().singleOrNull { it.deletedAt == null && it.isActive }
                if (existingSupplier != null) {
                    val mappingKey = "${existingSupplier.id.value}|${com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType.VENDOR_CODE}|$normalizedVendorCode"
                    val existingMappings = mappingsMap[mappingKey] ?: emptyList()
                    if (existingMappings.isNotEmpty()) {
                        issues.add(CsvImportRowIssue(HEADER_VENDOR_CODE, CsvImportIssueCode.EXISTING_VENDOR_MAPPING, CsvImportIssueSeverity.ERROR, listOf(normalizedVendorCode)))
                    }
                }
            }
        }

        // 6. Numeric Validation
        val packageConversion = if (!rawPackageConversion.isNullOrBlank()) {
            val value = rawPackageConversion.toBigDecimalOrNull()
            if (value == null || value <= BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_PACKAGE_CONVERSION, CsvImportIssueCode.INVALID_PACKAGE_CONVERSION, CsvImportIssueSeverity.ERROR))
                null
            } else value
        } else null

        if (!rawPurchasePackage.isNullOrBlank() && packageConversion == null) {
            issues.add(CsvImportRowIssue(HEADER_PACKAGE_CONVERSION, CsvImportIssueCode.PACKAGE_CONVERSION_REQUIRED, CsvImportIssueSeverity.ERROR))
        }

        val currentCost = if (!rawCurrentCost.isNullOrBlank()) {
            val value = rawCurrentCost.toBigDecimalOrNull()
            if (value == null || value < BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_CURRENT_COST, CsvImportIssueCode.INVALID_CURRENT_COST, CsvImportIssueSeverity.ERROR))
                null
            } else value
        } else null

        val reorderPoint = if (!rawReorderPoint.isNullOrBlank()) {
            val value = rawReorderPoint.toBigDecimalOrNull()
            if (value == null || value < BigDecimal.ZERO) {
                issues.add(CsvImportRowIssue(HEADER_REORDER_POINT, CsvImportIssueCode.INVALID_REORDER_POINT, CsvImportIssueSeverity.ERROR))
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

private object UnitImportAliases {
    private val aliases = mapOf(
        "mass_g" to setOf("g", "gram", "grams"),
        "mass_kg" to setOf("kg", "kgs", "kilogram", "kilograms", "kilo", "kilos"),
        "mass_oz" to setOf("oz", "ounce", "ounces"),
        "mass_lb" to setOf("lb", "lbs", "pound", "pounds"),
        "volume_ml" to setOf("ml", "milliliter", "milliliters"),
        "volume_l" to setOf("l", "liter", "liters", "litre", "litres"),
        "volume_tsp_us" to setOf("tsp", "teaspoon", "teaspoons"),
        "volume_tbsp_us" to setOf("tbsp", "tablespoon", "tablespoons"),
        "volume_fl_oz_us" to setOf("fl oz", "floz", "fluid ounce", "fluid ounces"),
        "volume_cup_us" to setOf("cup", "cups"),
        "volume_pint_us" to setOf("pt", "pint", "pints"),
        "volume_quart_us" to setOf("qt", "quart", "quarts"),
        "volume_gallon_us" to setOf("gal", "gallon", "gallons"),
        "count_each" to setOf("ea", "each", "piece", "pieces", "unit", "units")
    )
    fun aliasesFor(unitId: String): Set<String> = aliases[unitId].orEmpty()
}
