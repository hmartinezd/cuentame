package com.miara.cuentame.core.backup.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupSnapshotDto(
    val restaurants: List<RestaurantBackupDto>,
    val inventoryAreas: List<InventoryAreaBackupDto>,
    val ingredientCategories: List<IngredientCategoryBackupDto>,
    val units: List<UnitBackupDto>,
    val ingredients: List<IngredientBackupDto>,
    val ingredientUnitOptions: List<IngredientUnitOptionBackupDto>,
    val suppliers: List<SupplierBackupDto>,
    val purchaseReceipts: List<PurchaseReceiptBackupDto>,
    val purchaseLines: List<PurchaseLineBackupDto>,
    val stockCounts: List<StockCountBackupDto>,
    val stockCountAreas: List<StockCountAreaBackupDto>,
    val stockCountLines: List<StockCountLineBackupDto>,
    val wasteEvents: List<WasteEventBackupDto>,
    val inventoryMovements: List<InventoryMovementBackupDto>,
    val inventoryBalanceProjections: List<InventoryBalanceProjectionBackupDto>,
    val ingredientCostProjections: List<IngredientCostProjectionBackupDto>,
    val preparationRecipes: List<PreparationRecipeBackupDto> = emptyList(),
    val preparationRecipeComponents: List<PreparationRecipeComponentBackupDto> = emptyList(),
    val productionBatches: List<ProductionBatchBackupDto> = emptyList(),
    val productionBatchComponents: List<ProductionBatchComponentBackupDto> = emptyList(),
    val purchaseInvoiceOcrResults: List<PurchaseInvoiceOcrResultBackupDto> = emptyList(),
    val purchaseInvoiceOcrPages: List<PurchaseInvoiceOcrPageBackupDto> = emptyList(),
    val purchaseInvoiceParseResults: List<PurchaseInvoiceParseResultBackupDto> = emptyList(),
    val purchaseInvoiceParsedLines: List<PurchaseInvoiceParsedLineBackupDto> = emptyList(),
    val supplierItemMappings: List<SupplierItemMappingBackupDto> = emptyList(),
    val purchaseInvoiceLineMatches: List<PurchaseInvoiceLineMatchBackupDto> = emptyList(),
    val purchaseInvoiceDraftApplications: List<PurchaseInvoiceDraftApplicationBackupDto> = emptyList(),
    val purchaseInvoiceLineOrigins: List<PurchaseInvoiceLineOriginBackupDto> = emptyList(),
    val stockCountItemOrder: List<StockCountItemOrderBackupDto> = emptyList(),
    val menuRecipes: List<MenuRecipeBackupDto> = emptyList(),
    val menuRecipeComponents: List<MenuRecipeComponentBackupDto> = emptyList(),
    val menus: List<MenuBackupDto> = emptyList(),
    val menuCategories: List<MenuCategoryBackupDto> = emptyList(),
    val menuPlacements: List<MenuPlacementBackupDto> = emptyList()
)

@Serializable
data class MenuRecipeBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sellingPrice: String?,
    val notes: String?,
    val cashDiscountBehavior: String = "APPLY_DEFAULT",
    val commercialRevision: Long = 0,
    val consumptionRevision: Long = 0,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable data class MenuBackupDto(val id:String,val restaurantId:String,val name:String,val normalizedName:String,
    val description:String?,val defaultCashDiscountPercent:String,val publicationRevision:Long=0,val archivedAt:Long?,val createdAt:Long,val updatedAt:Long)
@Serializable data class MenuCategoryBackupDto(val id:String,val menuId:String,val name:String,val normalizedName:String,val sortOrder:Int)
@Serializable data class MenuPlacementBackupDto(val id:String,val menuId:String,val categoryId:String,val menuRecipeId:String,val sortOrder:Int)

@Serializable
data class MenuRecipeComponentBackupDto(
    val id: String,
    val menuRecipeId: String,
    val ingredientId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class StockCountItemOrderBackupDto(
    val restaurantId: String,
    val areaId: String,
    val ingredientId: String,
    val sortOrder: Int,
    val updatedAt: Long
)

@Serializable
data class PurchaseInvoiceDraftApplicationBackupDto(
    val id: String,
    val purchaseReceiptId: String,
    val parseResultId: String,
    val sourceDocumentSha256: String,
    val sourceStateFingerprint: String,
    val appliedAt: Long,
    val duplicateOverrideType: String? = null,
    val duplicateExistingReceiptId: String? = null,
    val duplicateNormalizedInvoiceNumber: String? = null,
    val duplicateSourceSha256: String? = null,
    val duplicateOverriddenAt: Long? = null
)

@Serializable
data class PurchaseInvoiceLineOriginBackupDto(
    val purchaseLineId: String,
    val applicationId: String,
    val sourceLineIndex: Int,
    val sourceStateFingerprint: String,
    val lastMaterializedSnapshotJson: String
)

@Serializable
data class SupplierItemMappingBackupDto(
    val id: String,
    val restaurantId: String,
    val supplierId: String,
    val keyType: String,
    val normalizedKey: String,
    val sourceVendorCode: String?,
    val sourceDescription: String?,
    val sourcePackageText: String?,
    val ingredientId: String,
    val unitOptionId: String?,
    val inventoryAreaId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastConfirmedAt: Long
)

@Serializable
data class PurchaseInvoiceLineMatchBackupDto(
    val parseResultId: String,
    val lineIndex: Int,
    val status: String,
    val supplierId: String?,
    val ingredientId: String?,
    val unitOptionId: String?,
    val inventoryAreaId: String?,
    val mappingId: String?,
    val matchMethod: String?,
    val matchConfidence: Float,
    val confirmedAt: Long?
)

@Serializable
data class PurchaseInvoiceParseResultBackupDto(
    val id: String,
    val purchaseReceiptId: String,
    val ocrResultId: String,
    val sourceDocumentSha256: String,
    val parserEngine: String,
    val parserSchemaVersion: Int,
    val headerEvidenceJson: String,
    val totalsEvidenceJson: String,
    val correctionsJson: String?,
    val warningsJson: String,
    val processedAt: Long,
    val reviewedAt: Long?
)

@Serializable
data class PurchaseInvoiceParsedLineBackupDto(
    val parseResultId: String,
    val lineIndex: Int,
    val evidenceJson: String,
    val correctionJson: String?,
    val isIgnored: Boolean
)

@Serializable
data class PurchaseInvoiceOcrResultBackupDto(
    val id: String,
    val purchaseReceiptId: String,
    val sourceDocumentSha256: String,
    val sourceMimeType: String,
    val engine: String,
    val evidenceSchemaVersion: Int,
    val pageCount: Int,
    val fullText: String,
    val processedAt: Long
)

@Serializable
data class PurchaseInvoiceOcrPageBackupDto(
    val ocrResultId: String,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val text: String,
    val evidenceJson: String
)

@Serializable
data class PreparationRecipeBackupDto(
    val id: String,
    val restaurantId: String,
    val outputIngredientId: String,
    val name: String,
    val normalizedName: String,
    val standardYieldQuantity: String?,
    val standardYieldQuantityBase: String?,
    val yieldUnitOptionId: String?,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?
)

@Serializable
data class PreparationRecipeComponentBackupDto(
    val id: String,
    val recipeId: String,
    val componentIngredientId: String,
    val unitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val sortOrder: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ProductionBatchBackupDto(
    val id: String,
    val restaurantId: String,
    val recipeId: String,
    val recipeNameSnapshot: String,
    val outputIngredientId: String,
    val batchMultiplier: String,
    val recipeStandardYieldQuantitySnapshot: String,
    val recipeStandardYieldBaseSnapshot: String,
    val recipeYieldUnitOptionIdSnapshot: String,
    val expectedOutputQuantityEntered: String,
    val expectedOutputQuantityBase: String,
    val actualOutputQuantityEntered: String,
    val actualOutputQuantityBase: String,
    val outputUnitOptionId: String,
    val outputAreaId: String,
    val hasManualOutputQuantityOverride: Boolean,
    val totalComponentCostSnapshot: String?,
    val outputUnitCostBaseSnapshot: String?,
    val effectiveAt: Long,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)

@Serializable
data class ProductionBatchComponentBackupDto(
    val id: String,
    val productionBatchId: String,
    val sourceRecipeComponentIdSnapshot: String,
    val componentIngredientId: String,
    val recipeQuantityEnteredSnapshot: String,
    val recipeQuantityBaseSnapshot: String,
    val recipeUnitOptionIdSnapshot: String,
    val expectedQuantityEntered: String,
    val expectedQuantityBase: String,
    val actualQuantityEntered: String,
    val actualQuantityBase: String,
    val unitOptionId: String,
    val hasManualQuantityOverride: Boolean,
    val sourceAreaId: String?,
    val unitCostBaseSnapshot: String?,
    val totalCostSnapshot: String?,
    val sortOrder: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class RestaurantBackupDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val localeTag: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class InventoryAreaBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class IngredientCategoryBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class UnitBackupDto(
    val id: String,
    val name: String,
    val symbol: String,
    val dimension: String,
    val factorToCanonical: String,
    val isSystem: Boolean,
    val sortOrder: Int
)

@Serializable
data class IngredientBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val categoryId: String?,
    val baseUnitId: String,
    val defaultAreaId: String?,
    val sku: String?,
    val notes: String?,
    val reorderPointBase: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val parLevelBase: String? = null
)

@Serializable
data class IngredientUnitOptionBackupDto(
    val id: String,
    val ingredientId: String,
    val displayName: String,
    val shortLabel: String,
    val standardUnitId: String?,
    val factorToBase: String,
    val isBase: Boolean,
    val isDefaultCount: Boolean,
    val isDefaultPurchase: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SupplierBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val phone: String?,
    val email: String?,
    val notes: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class PurchaseReceiptBackupDto(
    val id: String,
    val restaurantId: String,
    val supplierId: String?,
    val invoiceNumber: String?,
    val purchaseDate: Long,
    val status: String,
    val notes: String?,
    val attachmentId: String?,
    val attachmentDisplayName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)

@Serializable
data class PurchaseLineBackupDto(
    val id: String,
    val purchaseReceiptId: String,
    val ingredientId: String,
    val areaId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val unitCostBase: String,
    val lineTotal: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class StockCountBackupDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val startedAt: Long,
    val effectiveAt: Long,
    val completedAt: Long?,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val voidedAt: Long?
)

@Serializable
data class StockCountAreaBackupDto(
    val id: String,
    val stockCountId: String,
    val areaId: String,
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val sortOrder: Int
)

@Serializable
data class StockCountLineBackupDto(
    val id: String,
    val stockCountAreaId: String,
    val ingredientId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val expectedQuantityBaseSnapshot: String?,
    val adjustmentQuantityBase: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class WasteEventBackupDto(
    val id: String,
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val reason: String,
    val effectiveAt: Long,
    val notes: String?,
    val attachmentId: String?,
    val attachmentDisplayName: String? = null,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)

@Serializable
data class InventoryMovementBackupDto(
    val id: String,
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val movementType: String,
    val quantityBaseSigned: String,
    val unitCostBaseSnapshot: String?,
    val totalValueSnapshot: String?,
    val effectiveAt: Long,
    val sourceDocumentType: String,
    val sourceDocumentId: String,
    val sourceOperationId: String,
    val sourceLineId: String?,
    val reversalOfMovementId: String?,
    val createdAt: Long
)

@Serializable
data class InventoryBalanceProjectionBackupDto(
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val quantityBase: String,
    val updatedAt: Long
)

@Serializable
data class IngredientCostProjectionBackupDto(
    val restaurantId: String,
    val ingredientId: String,
    val averageUnitCostBase: String?,
    val updatedAt: Long
)
