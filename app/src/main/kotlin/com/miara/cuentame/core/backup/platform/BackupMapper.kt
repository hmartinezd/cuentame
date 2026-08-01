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
                .sortedWith(compareBy({ it.restaurantId }, { it.ingredientId })),
            preparationRecipes = snapshot.preparationRecipes.map { it.toDto() }.sortedBy { it.id },
            preparationRecipeComponents = snapshot.preparationRecipeComponents.map { it.toDto() }.sortedBy { it.id }
        )
    }

    internal fun RestaurantEntity.toDto() = RestaurantBackupDto(
        id = id,
        name = name,
        currencyCode = currencyCode,
        localeTag = localeTag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    internal fun InventoryAreaEntity.toDto() = InventoryAreaBackupDto(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        sortOrder = sortOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    internal fun IngredientCategoryEntity.toDto() = IngredientCategoryBackupDto(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        sortOrder = sortOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    internal fun UnitEntity.toDto() = UnitBackupDto(
        id = id,
        name = name,
        symbol = symbol,
        dimension = dimension,
        factorToCanonical = factorToCanonical.toNormalizedString(),
        isSystem = isSystem,
        sortOrder = sortOrder
    )

    internal fun IngredientEntity.toDto() = IngredientBackupDto(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        categoryId = categoryId,
        baseUnitId = baseUnitId,
        defaultAreaId = defaultAreaId,
        sku = sku,
        notes = notes,
        reorderPointBase = reorderPointBase?.toNormalizedString(),
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    internal fun IngredientUnitOptionEntity.toDto() = IngredientUnitOptionBackupDto(
        id = id,
        ingredientId = ingredientId,
        displayName = displayName,
        shortLabel = shortLabel,
        standardUnitId = standardUnitId,
        factorToBase = factorToBase.toNormalizedString(),
        isBase = isBase,
        isDefaultCount = isDefaultCount,
        isDefaultPurchase = isDefaultPurchase,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    internal fun SupplierEntity.toDto() = SupplierBackupDto(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        phone = phone,
        email = email,
        notes = notes,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
    
    internal fun PurchaseReceiptEntity.toDto(attachmentIdMap: Map<String, String>) = PurchaseReceiptBackupDto(
        id = id,
        restaurantId = restaurantId,
        supplierId = supplierId,
        invoiceNumber = invoiceNumber,
        purchaseDate = purchaseDate,
        status = status,
        notes = notes, 
        attachmentId = attachmentPath?.let { attachmentIdMap[it] }, 
        createdAt = createdAt,
        updatedAt = updatedAt,
        postedAt = postedAt,
        voidedAt = voidedAt
    )
    
    internal fun PurchaseLineEntity.toDto() = PurchaseLineBackupDto(
        id = id,
        purchaseReceiptId = purchaseReceiptId,
        ingredientId = ingredientId,
        areaId = areaId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        unitCostBase = unitCostBase,
        lineTotal = lineTotal,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    internal fun StockCountEntity.toDto() = StockCountBackupDto(
        id = id,
        restaurantId = restaurantId,
        name = name,
        startedAt = startedAt,
        effectiveAt = effectiveAt,
        completedAt = completedAt,
        status = status,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        voidedAt = voidedAt
    )
    
    internal fun StockCountAreaEntity.toDto() = StockCountAreaBackupDto(
        id = id,
        stockCountId = stockCountId,
        areaId = areaId,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        sortOrder = sortOrder
    )

    internal fun StockCountLineEntity.toDto() = StockCountLineBackupDto(
        id = id,
        stockCountAreaId = stockCountAreaId,
        ingredientId = ingredientId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot,
        adjustmentQuantityBase = adjustmentQuantityBase,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    internal fun WasteEventEntity.toDto(attachmentIdMap: Map<String, String>) = WasteEventBackupDto(
        id = id,
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        reason = reason,
        effectiveAt = effectiveAt,
        notes = notes, 
        attachmentId = attachmentPath?.let { attachmentIdMap[it] }, 
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        postedAt = postedAt,
        voidedAt = voidedAt
    )
    
    internal fun InventoryMovementEntity.toDto() = InventoryMovementBackupDto(
        id = id,
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        movementType = movementType,
        quantityBaseSigned = quantityBaseSigned,
        unitCostBaseSnapshot = unitCostBaseSnapshot,
        totalValueSnapshot = totalValueSnapshot,
        effectiveAt = effectiveAt,
        sourceDocumentType = sourceDocumentType,
        sourceDocumentId = sourceDocumentId,
        sourceOperationId = sourceOperationId,
        sourceLineId = sourceLineId,
        reversalOfMovementId = reversalOfMovementId,
        createdAt = createdAt
    )

    internal fun InventoryBalanceProjectionEntity.toDto() = InventoryBalanceProjectionBackupDto(
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        quantityBase = quantityBase,
        updatedAt = updatedAt
    )

    internal fun IngredientCostProjectionEntity.toDto() = IngredientCostProjectionBackupDto(
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        averageUnitCostBase = averageUnitCostBase,
        updatedAt = updatedAt
    )

    internal fun PreparationRecipeEntity.toDto() = PreparationRecipeBackupDto(
        id = id,
        restaurantId = restaurantId,
        outputIngredientId = outputIngredientId,
        name = name,
        normalizedName = normalizedName,
        standardYieldQuantity = standardYieldQuantity?.toNormalizedString(),
        standardYieldQuantityBase = standardYieldQuantityBase?.toNormalizedString(),
        yieldUnitOptionId = yieldUnitOptionId,
        status = status,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt
    )

    internal fun PreparationRecipeComponentEntity.toDto() = PreparationRecipeComponentBackupDto(
        id = id,
        recipeId = recipeId,
        componentIngredientId = componentIngredientId,
        unitOptionId = unitOptionId,
        quantityEntered = quantityEntered.toNormalizedString(),
        quantityBase = quantityBase.toNormalizedString(),
        sortOrder = sortOrder,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun java.math.BigDecimal.toNormalizedString(): String {
        return if (this.compareTo(java.math.BigDecimal.ZERO) == 0) "0"
        else this.stripTrailingZeros().toPlainString()
    }


    fun RestaurantBackupDto.toEntity() = RestaurantEntity(
        id = id,
        name = name,
        currencyCode = currencyCode,
        localeTag = localeTag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    fun InventoryAreaBackupDto.toEntity() = InventoryAreaEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        sortOrder = sortOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    fun IngredientCategoryBackupDto.toEntity() = IngredientCategoryEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        sortOrder = sortOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    fun UnitBackupDto.toEntity() = UnitEntity(
        id = id,
        name = name,
        symbol = symbol,
        dimension = dimension,
        factorToCanonical = java.math.BigDecimal(factorToCanonical),
        isSystem = isSystem,
        sortOrder = sortOrder
    )

    fun SupplierBackupDto.toEntity() = SupplierEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        phone = phone,
        email = email,
        notes = notes,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    fun IngredientBackupDto.toEntity() = IngredientEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = normalizedName,
        categoryId = categoryId,
        baseUnitId = baseUnitId,
        defaultAreaId = defaultAreaId,
        sku = sku,
        notes = notes,
        reorderPointBase = reorderPointBase?.let { java.math.BigDecimal(it) },
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    fun IngredientUnitOptionBackupDto.toEntity() = IngredientUnitOptionEntity(
        id = id,
        ingredientId = ingredientId,
        displayName = displayName,
        shortLabel = shortLabel,
        standardUnitId = standardUnitId,
        factorToBase = java.math.BigDecimal(factorToBase),
        isBase = isBase,
        isDefaultCount = isDefaultCount,
        isDefaultPurchase = isDefaultPurchase,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
    
    fun PurchaseReceiptBackupDto.toEntity() = PurchaseReceiptEntity(
        id = id,
        restaurantId = restaurantId,
        supplierId = supplierId,
        invoiceNumber = invoiceNumber,
        purchaseDate = purchaseDate,
        status = status,
        notes = notes,
        attachmentPath = attachmentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        postedAt = postedAt,
        voidedAt = voidedAt
    )

    fun PurchaseLineBackupDto.toEntity() = PurchaseLineEntity(
        id = id,
        purchaseReceiptId = purchaseReceiptId,
        ingredientId = ingredientId,
        areaId = areaId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        lineTotal = lineTotal,
        unitCostBase = unitCostBase,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    fun StockCountBackupDto.toEntity() = StockCountEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        startedAt = startedAt,
        effectiveAt = effectiveAt,
        completedAt = completedAt,
        status = status,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        voidedAt = voidedAt
    )

    fun StockCountAreaBackupDto.toEntity() = StockCountAreaEntity(
        id = id,
        stockCountId = stockCountId,
        areaId = areaId,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        sortOrder = sortOrder
    )

    fun StockCountLineBackupDto.toEntity() = StockCountLineEntity(
        id = id,
        stockCountAreaId = stockCountAreaId,
        ingredientId = ingredientId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot,
        adjustmentQuantityBase = adjustmentQuantityBase,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    fun WasteEventBackupDto.toEntity() = WasteEventEntity(
        id = id,
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        ingredientUnitOptionId = ingredientUnitOptionId,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        reason = reason,
        effectiveAt = effectiveAt,
        notes = notes,
        attachmentPath = attachmentId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        postedAt = postedAt,
        voidedAt = voidedAt
    )
    
    fun InventoryMovementBackupDto.toEntity() = InventoryMovementEntity(
        id = id,
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        movementType = movementType,
        quantityBaseSigned = quantityBaseSigned,
        unitCostBaseSnapshot = unitCostBaseSnapshot,
        totalValueSnapshot = totalValueSnapshot,
        effectiveAt = effectiveAt,
        sourceDocumentType = sourceDocumentType,
        sourceDocumentId = sourceDocumentId,
        sourceOperationId = sourceOperationId,
        sourceLineId = sourceLineId,
        reversalOfMovementId = reversalOfMovementId,
        createdAt = createdAt
    )

    fun InventoryBalanceProjectionBackupDto.toEntity() = InventoryBalanceProjectionEntity(
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        areaId = areaId,
        quantityBase = quantityBase,
        updatedAt = updatedAt
    )

    fun IngredientCostProjectionBackupDto.toEntity() = IngredientCostProjectionEntity(
        restaurantId = restaurantId,
        ingredientId = ingredientId,
        averageUnitCostBase = averageUnitCostBase,
        updatedAt = updatedAt
    )

    fun PreparationRecipeBackupDto.toEntity() = PreparationRecipeEntity(
        id = id,
        restaurantId = restaurantId,
        outputIngredientId = outputIngredientId,
        name = name,
        normalizedName = normalizedName,
        standardYieldQuantity = standardYieldQuantity?.let { java.math.BigDecimal(it) },
        standardYieldQuantityBase = standardYieldQuantityBase?.let { java.math.BigDecimal(it) },
        yieldUnitOptionId = yieldUnitOptionId,
        status = status,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt
    )

    fun PreparationRecipeComponentBackupDto.toEntity() = PreparationRecipeComponentEntity(
        id = id,
        recipeId = recipeId,
        componentIngredientId = componentIngredientId,
        unitOptionId = unitOptionId,
        quantityEntered = java.math.BigDecimal(quantityEntered),
        quantityBase = java.math.BigDecimal(quantityBase),
        sortOrder = sortOrder,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

}
