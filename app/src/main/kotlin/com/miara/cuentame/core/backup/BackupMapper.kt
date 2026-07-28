package com.miara.cuentame.core.backup

import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.backup.model.*

object BackupMapper {

    fun mapToDto(
        snapshot: com.miara.cuentame.core.model.backup.BackupSnapshot,
        attachmentIdMap: Map<String, String> // URI -> ID
    ): BackupSnapshotDto {
        return BackupSnapshotDto(
            restaurants = snapshot.restaurants.map { it.toDto() },
            inventoryAreas = snapshot.inventoryAreas.map { it.toDto() },
            ingredientCategories = snapshot.ingredientCategories.map { it.toDto() },
            units = snapshot.units.map { it.toDto() },
            ingredients = snapshot.ingredients.map { it.toDto() },
            ingredientUnitOptions = snapshot.ingredientUnitOptions.map { it.toDto() },
            suppliers = snapshot.suppliers.map { it.toDto() },
            purchaseReceipts = snapshot.purchaseReceipts.map { it.toDto(attachmentIdMap) },
            purchaseLines = snapshot.purchaseLines.map { it.toDto() },
            stockCounts = snapshot.stockCounts.map { it.toDto() },
            stockCountAreas = snapshot.stockCountAreas.map { it.toDto() },
            stockCountLines = snapshot.stockCountLines.map { it.toDto() },
            wasteEvents = snapshot.wasteEvents.map { it.toDto(attachmentIdMap) },
            inventoryMovements = snapshot.inventoryMovements.map { it.toDto() },
            inventoryBalanceProjections = snapshot.inventoryBalanceProjections.map { it.toDto() },
            ingredientCostProjections = snapshot.ingredientCostProjections.map { it.toDto() }
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
}
