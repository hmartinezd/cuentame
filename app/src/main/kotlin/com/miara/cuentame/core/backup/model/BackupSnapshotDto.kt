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
    val preparationRecipeComponents: List<PreparationRecipeComponentBackupDto> = emptyList()
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
    val deletedAt: Long?
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
