package com.venkoi.restaurantops.core.database.model

import androidx.room.Embedded
import com.venkoi.restaurantops.core.database.entity.InventoryMovementEntity
import java.time.Instant

data class InventoryActivityRow(
    @Embedded val movement: InventoryMovementEntity,
    
    val ingredientName: String,
    val areaName: String,
    val baseUnitSymbol: String,
    
    // Source document info
    val sourcePurchaseSupplierName: String? = null,
    val sourcePurchaseInvoiceNumber: String? = null,
    
    val sourceWasteReason: String? = null,
    val sourceWasteAreaName: String? = null,
    
    val sourceStockCountName: String? = null,
    
    val sourceProductionRecipeName: String? = null,
    val sourceProductionStatus: String? = null,

    // Source resolution markers
    val sourcePurchaseResolvedId: String? = null,
    val sourceWasteResolvedId: String? = null,
    val sourceStockCountResolvedId: String? = null,
    val sourceProductionResolvedId: String? = null,

    // Reversal info
    val reversedByMovementId: String? = null,
    val reversedByMovementType: String? = null,
    val reversedByMovementEffectiveAt: Instant? = null,
    
    val reversalOfMovementType: String? = null,
    val reversalOfMovementEffectiveAt: Instant? = null
)
