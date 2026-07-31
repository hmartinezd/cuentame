package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.database.backup.BackupSnapshot

object BackupMapper {

    fun mapToDto(
        snapshot: BackupSnapshot,
        attachmentIdMap: Map<String, String> // URI -> ID
    ): BackupSnapshotDto {
        return BackupSnapshotDto(
            restaurants = snapshot.restaurants.map { it.toDto() }.sortedBy { it.id },
            inventoryAreas = snapshot.inventoryAreas.map { it.toDto() }.sortedBy { it.id },
            ingredientCategories = snapshot.ingredientCategories.map { it.toDto() }.sortedBy { it.id },
            units = snapshot.units.map { it.toDto() }.sortedBy { it.id },
            ingredients = snapshot.ingredients.map { it.toDto() }.sortedBy { it.id },
            ingredientUnitOptions = snapshot.ingredientUnitOptions.map { it.toDto() }.sortedBy { it.id },
            suppliers = snapshot.suppliers.map { it.toDto() }.sortedBy { it.id },
            purchaseReceipts = snapshot.purchaseReceipts.map { it.toDto(attachmentIdMap) }.sortedBy { it.id },
            purchaseLines = snapshot.purchaseLines.map { it.toDto() }.sortedBy { it.id },
            stockCounts = snapshot.stockCounts.map { it.toDto() }.sortedBy { it.id },
            stockCountAreas = snapshot.stockCountAreas.map { it.toDto() }.sortedBy { it.id },
            stockCountLines = snapshot.stockCountLines.map { it.toDto() }.sortedBy { it.id },
            wasteEvents = snapshot.wasteEvents.map { it.toDto(attachmentIdMap) }.sortedBy { it.id },
            inventoryMovements = snapshot.inventoryMovements.map { it.toDto() }.sortedBy { it.id },
            inventoryBalanceProjections = snapshot.inventoryBalanceProjections.map { it.toDto() }
                .sortedWith(compareBy({ it.restaurantId }, { it.ingredientId }, { it.areaId })),
            ingredientCostProjections = snapshot.ingredientCostProjections.map { it.toDto() }
                .sortedWith(compareBy({ it.restaurantId }, { it.ingredientId }))
        )
    }

    private fun RestaurantEntity.toDto() = RestaurantBackupDto(id, name, currencyCode, localeTag, createdAt, updatedAt, deletedAt)
    private fun InventoryAreaEntity.toDto() = InventoryAreaBackupDto(id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt, deletedAt)
    private fun IngredientCategoryEntity.toDto() = IngredientCategoryBackupDto(id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt, deletedAt)
    private fun UnitEntity.toDto() = UnitBackupDto(id, name, symbol, dimension, factorToCanonical.toPlainString(), isSystem, sortOrder)
    private fun IngredientEntity.toDto() = IngredientBackupDto(id, restaurantId, name, normalizedName, categoryId, baseUnitId, defaultAreaId, sku, notes, reorderPointBase?.toPlainString(), isActive, createdAt, updatedAt, deletedAt)
    private fun IngredientUnitOptionEntity.toDto() = IngredientUnitOptionBackupDto(id, ingredientId, displayName, shortLabel, standardUnitId, factorToBase.toPlainString(), isBase, isDefaultCount, isDefaultPurchase, isActive, createdAt, updatedAt, deletedAt)
    private fun SupplierEntity.toDto() = SupplierBackupDto(id, restaurantId, name, normalizedName, phone, email, notes, isActive, createdAt, updatedAt, deletedAt)
    
    private fun PurchaseReceiptEntity.toDto(attachmentIdMap: Map<String, String>) = PurchaseReceiptBackupDto(
        id, restaurantId, supplierId, invoiceNumber, purchaseDate, status, notes, 
        attachmentPath?.let { attachmentIdMap[it] }, 
        createdAt, updatedAt, postedAt, voidedAt
    )
    
    private fun PurchaseLineEntity.toDto() = PurchaseLineBackupDto(id, purchaseReceiptId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, unitCostBase, lineTotal, notes, createdAt, updatedAt)
    
    private fun StockCountEntity.toDto() = StockCountBackupDto(id, restaurantId, name, startedAt, effectiveAt, completedAt, status, notes, createdAt, updatedAt, voidedAt)
    
    private fun StockCountAreaEntity.toDto() = StockCountAreaBackupDto(id, stockCountId, areaId, status, startedAt, completedAt, sortOrder)
    private fun StockCountLineEntity.toDto() = StockCountLineBackupDto(id, stockCountAreaId, ingredientId, ingredientUnitOptionId, quantityEntered, quantityBase, expectedQuantityBaseSnapshot, adjustmentQuantityBase, notes, createdAt, updatedAt)
    
    private fun WasteEventEntity.toDto(attachmentIdMap: Map<String, String>) = WasteEventBackupDto(
        id, restaurantId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, reason, effectiveAt, notes, 
        attachmentPath?.let { attachmentIdMap[it] }, 
        status, createdAt, updatedAt, postedAt, voidedAt
    )
    
    private fun InventoryMovementEntity.toDto() = InventoryMovementBackupDto(id, restaurantId, ingredientId, areaId, movementType, quantityBaseSigned, unitCostBaseSnapshot, totalValueSnapshot, effectiveAt, sourceDocumentType, sourceDocumentId, sourceOperationId, sourceLineId, reversalOfMovementId, createdAt)
    private fun InventoryBalanceProjectionEntity.toDto() = InventoryBalanceProjectionBackupDto(restaurantId, ingredientId, areaId, quantityBase, updatedAt)
    private fun IngredientCostProjectionEntity.toDto() = IngredientCostProjectionBackupDto(restaurantId, ingredientId, averageUnitCostBase, updatedAt)

    fun RestaurantBackupDto.toEntity() = RestaurantEntity(id, name, currencyCode, localeTag, createdAt, updatedAt, deletedAt)
    fun InventoryAreaBackupDto.toEntity() = InventoryAreaEntity(id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt, deletedAt)
    fun IngredientCategoryBackupDto.toEntity() = IngredientCategoryEntity(id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt, deletedAt)
    fun UnitBackupDto.toEntity() = UnitEntity(id, name, symbol, dimension, java.math.BigDecimal(factorToCanonical), isSystem, sortOrder)
    fun SupplierBackupDto.toEntity() = SupplierEntity(id, restaurantId, name, normalizedName, phone, email, notes, isActive, createdAt, updatedAt, deletedAt)
    fun IngredientBackupDto.toEntity() = IngredientEntity(id, restaurantId, name, normalizedName, categoryId, baseUnitId, defaultAreaId, sku, notes, reorderPointBase?.let { java.math.BigDecimal(it) }, isActive, createdAt, updatedAt, deletedAt)
    fun IngredientUnitOptionBackupDto.toEntity() = IngredientUnitOptionEntity(id, ingredientId, displayName, shortLabel, standardUnitId, java.math.BigDecimal(factorToBase), isBase, isDefaultCount, isDefaultPurchase, isActive, createdAt, updatedAt, deletedAt)
    
    fun PurchaseReceiptBackupDto.toEntity() = PurchaseReceiptEntity(id, restaurantId, supplierId, invoiceNumber, purchaseDate, status, notes, attachmentId, createdAt, updatedAt, postedAt, voidedAt)
    fun PurchaseLineBackupDto.toEntity() = PurchaseLineEntity(id, purchaseReceiptId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, unitCostBase, lineTotal, notes, createdAt, updatedAt)
    
    fun StockCountBackupDto.toEntity() = StockCountEntity(id, restaurantId, name, startedAt, effectiveAt, completedAt, status, notes, createdAt, updatedAt, voidedAt)
    fun StockCountAreaBackupDto.toEntity() = StockCountAreaEntity(id, stockCountId, areaId, status, startedAt, completedAt, sortOrder)
    fun StockCountLineBackupDto.toEntity() = StockCountLineEntity(id, stockCountAreaId, ingredientId, ingredientUnitOptionId, quantityEntered, quantityBase, expectedQuantityBaseSnapshot, adjustmentQuantityBase, notes, createdAt, updatedAt)
    
    fun WasteEventBackupDto.toEntity() = WasteEventEntity(id, restaurantId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, reason, effectiveAt, notes, attachmentId, status, createdAt, updatedAt, postedAt, voidedAt)
    
    fun InventoryMovementBackupDto.toEntity() = InventoryMovementEntity(id, restaurantId, ingredientId, areaId, movementType, quantityBaseSigned, unitCostBaseSnapshot, totalValueSnapshot, effectiveAt, sourceDocumentType, sourceDocumentId, sourceOperationId, sourceLineId, reversalOfMovementId, createdAt)
    fun InventoryBalanceProjectionBackupDto.toEntity() = InventoryBalanceProjectionEntity(restaurantId, ingredientId, areaId, quantityBase, updatedAt)
    fun IngredientCostProjectionBackupDto.toEntity() = IngredientCostProjectionEntity(restaurantId, ingredientId, averageUnitCostBase, updatedAt)
}
