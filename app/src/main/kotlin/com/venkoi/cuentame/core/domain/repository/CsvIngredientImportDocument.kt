package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientCategoryId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.common.ids.UnitId
import java.math.BigDecimal

data class CsvIngredientImportDocument(val rows: List<CsvIngredientImportRow>)
data class CsvIngredientImportRow(
    val rowNumber: Int,
    val rawData: Map<String, String>,
    val normalizedData: NormalizedIngredientData?,
    val issues: List<CsvImportRowIssue>,
    val status: CsvImportRowStatus,
    val isIncluded: Boolean = true
)
data class NormalizedIngredientData(
    val name: String,
    val sku: String?,
    val categoryName: String?,
    val resolvedCategoryId: IngredientCategoryId?,
    val baseUnitName: String,
    val resolvedBaseUnitId: UnitId?,
    val countUnitName: String?,
    val resolvedCountUnitId: UnitId?,
    val purchasePackageName: String?,
    val packageConversionFactor: BigDecimal?,
    val defaultAreaName: String?,
    val resolvedDefaultAreaId: InventoryAreaId?,
    val supplierName: String?,
    val resolvedSupplierId: SupplierId?,
    val vendorItemCode: String?,
    val currentCostPerBaseUnit: BigDecimal?,
    val reorderPointBase: BigDecimal?
)
data class CsvImportRowIssue(
    val field: String?,
    val code: CsvImportIssueCode,
    val severity: CsvImportIssueSeverity,
    val parameters: List<String> = emptyList()
)
enum class CsvImportIssueCode {
    INGREDIENT_NAME_REQUIRED, INVALID_INGREDIENT_NAME, DUPLICATE_INGREDIENT_NAME_IN_FILE,
    EXISTING_ACTIVE_INGREDIENT, EXISTING_ARCHIVED_INGREDIENT, DUPLICATE_SKU_IN_FILE, EXISTING_SKU,
    UNIT_REQUIRED, UNKNOWN_UNIT, AMBIGUOUS_UNIT, INCOMPATIBLE_COUNT_UNIT,
    CATEGORY_WILL_BE_CREATED, CATEGORY_ARCHIVED, CATEGORY_INACTIVE, CATEGORY_DATA_CONFLICT,
    SUPPLIER_WILL_BE_CREATED, SUPPLIER_ARCHIVED, SUPPLIER_INACTIVE, SUPPLIER_DATA_CONFLICT,
    UNKNOWN_AREA, VENDOR_CODE_REQUIRES_SUPPLIER, DUPLICATE_VENDOR_CODE, EXISTING_VENDOR_MAPPING,
    INVALID_PACKAGE_CONVERSION, PACKAGE_CONVERSION_REQUIRED, INVALID_CURRENT_COST, INVALID_REORDER_POINT
}
enum class CsvImportIssueSeverity { WARNING, ERROR }
enum class CsvImportRowStatus { READY, WARNING, ERROR, SKIPPED }
data class CsvImportSummary(val totalRows: Int, val readyRows: Int, val warningRows: Int, val errorRows: Int, val skippedRows: Int)
