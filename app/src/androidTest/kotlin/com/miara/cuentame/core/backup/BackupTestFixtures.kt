package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.*

import java.math.BigDecimal

object BackupTestFixtures {

    fun createEmptySnapshotDto() = BackupSnapshotDto(
        restaurants = emptyList(),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList(),
        preparationRecipes = emptyList(),
        preparationRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList()
    )

    fun addPostedPurchase(
        snapshot: BackupSnapshotDto,
        receiptId: String,
        lineId: String,
        movementId: String,
        ingredientId: String,
        areaId: String,
        optionId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = quantityBase.multiply(unitCostBase, java.math.MathContext.DECIMAL128)

        val receipt = PurchaseReceiptBackupDto(
            id = receiptId,
            restaurantId = restaurantId,
            supplierId = null,
            invoiceNumber = "INV-$receiptId",
            purchaseDate = effectiveAt,
            status = "POSTED",
            notes = null,
            attachmentId = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val line = PurchaseLineBackupDto(
            id = lineId,
            purchaseReceiptId = receiptId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = optionId,
            quantityEntered = quantityBase.toPlainString(),
            quantityBase = quantityBase.toPlainString(),
            unitCostBase = unitCostBase.toPlainString(),
            lineTotal = totalValue.toPlainString(),
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val movement = InventoryMovementBackupDto(
            id = movementId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            movementType = "PURCHASE",
            quantityBaseSigned = quantityBase.toPlainString(),
            unitCostBaseSnapshot = unitCostBase.toPlainString(),
            totalValueSnapshot = totalValue.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PURCHASE_RECEIPT",
            sourceDocumentId = receiptId,
            sourceOperationId = "purchase-post:$receiptId:line:$lineId",
            sourceLineId = lineId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            purchaseReceipts = snapshot.purchaseReceipts + receipt,
            purchaseLines = snapshot.purchaseLines + line,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }
}
