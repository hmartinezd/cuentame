package com.miara.cuentame.feature.ingredient.import.domain

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.ids.UnitId
import java.math.BigDecimal

data class CsvIngredientImportDocument(
    val rows: List<CsvIngredientImportRow>
)

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
    val message: String,
    val severity: CsvImportIssueSeverity
)

enum class CsvImportIssueSeverity {
    WARNING,
    ERROR
}

enum class CsvImportRowStatus {
    READY,
    WARNING,
    ERROR,
    SKIPPED
}

data class CsvImportSummary(
    val totalRows: Int,
    val readyRows: Int,
    val warningRows: Int,
    val errorRows: Int,
    val skippedRows: Int
)
