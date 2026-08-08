package com.miara.cuentame.core.ocr.parser.matching

import java.math.BigDecimal

data class EffectiveParsedInvoiceLine(
    val vendorCode: String?,
    val description: String?,
    val packageText: String?,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
    val lineTotal: BigDecimal?
)

data class InventoryMatchResult(
    val knownMapping: InventoryMatchCandidate?,
    val candidates: List<InventoryMatchCandidate>,
    val warnings: List<String> = emptyList()
)

data class InventoryMatchCandidate(
    val ingredientId: String,
    val unitOptionId: String? = null,
    val inventoryAreaId: String? = null,
    val reason: MatchReason,
    val confidence: Float,
    val mappingId: String? = null
)

enum class MatchReason {
    KnownSupplierItem,
    ExactIngredientName,
    SimilarDescription,
    PackageCompatibility,
    DescriptionAndPackageMatch
}

data class InventoryCatalog(
    val ingredients: List<IngredientMatchModel>,
    val supplierMappings: List<SupplierItemMappingMatchModel>
)

data class IngredientMatchModel(
    val id: String,
    val name: String,
    val normalizedName: String,
    val defaultAreaId: String?,
    val unitOptions: List<UnitOptionMatchModel>
)

data class UnitOptionMatchModel(
    val id: String,
    val name: String,
    val normalizedName: String
)

data class SupplierItemMappingMatchModel(
    val id: String,
    val supplierId: String,
    val keyType: String, // VENDOR_CODE or DESCRIPTION_PACKAGE
    val normalizedKey: String,
    val ingredientId: String,
    val unitOptionId: String?,
    val inventoryAreaId: String?
)
