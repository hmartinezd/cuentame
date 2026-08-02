package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
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
            sourceOperationId = InventoryMovementOperationIds.purchasePost(receiptId, lineId),
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

    fun addPostedWaste(
        snapshot: BackupSnapshotDto,
        eventId: String,
        movementId: String,
        ingredientId: String,
        areaId: String,
        optionId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal?,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = unitCostBase?.multiply(quantityBase.negate(), java.math.MathContext.DECIMAL128)

        val event = WasteEventBackupDto(
            id = eventId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = optionId,
            quantityEntered = quantityBase.toPlainString(),
            quantityBase = quantityBase.toPlainString(),
            reason = "EXPIRED",
            effectiveAt = effectiveAt,
            notes = null,
            attachmentId = null,
            status = "POSTED",
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val movement = InventoryMovementBackupDto(
            id = movementId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            movementType = "WASTE",
            quantityBaseSigned = quantityBase.negate().toPlainString(),
            unitCostBaseSnapshot = unitCostBase?.toPlainString(),
            totalValueSnapshot = totalValue?.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "WASTE_EVENT",
            sourceDocumentId = eventId,
            sourceOperationId = InventoryMovementOperationIds.wastePost(eventId),
            sourceLineId = eventId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            wasteEvents = snapshot.wasteEvents + event,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }

    fun addPostedProduction(
        snapshot: BackupSnapshotDto,
        batchId: String,
        outputMovementId: String,
        outputIngredientId: String,
        outputAreaId: String,
        outputOptionId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = quantityBase.multiply(unitCostBase, java.math.MathContext.DECIMAL128)

        val batch = ProductionBatchBackupDto(
            id = batchId,
            restaurantId = restaurantId,
            recipeId = "recipe1",
            recipeNameSnapshot = "Test Recipe",
            outputIngredientId = outputIngredientId,
            outputAreaId = outputAreaId,
            outputUnitOptionId = outputOptionId,
            batchMultiplier = "1",
            recipeStandardYieldQuantitySnapshot = quantityBase.toPlainString(),
            recipeStandardYieldBaseSnapshot = quantityBase.toPlainString(),
            recipeYieldUnitOptionIdSnapshot = outputOptionId,
            expectedOutputQuantityEntered = quantityBase.toPlainString(),
            expectedOutputQuantityBase = quantityBase.toPlainString(),
            actualOutputQuantityEntered = quantityBase.toPlainString(),
            actualOutputQuantityBase = quantityBase.toPlainString(),
            hasManualOutputQuantityOverride = false,
            totalComponentCostSnapshot = totalValue.toPlainString(),
            outputUnitCostBaseSnapshot = unitCostBase.toPlainString(),
            status = "POSTED",
            effectiveAt = effectiveAt,
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val movement = InventoryMovementBackupDto(
            id = outputMovementId,
            restaurantId = restaurantId,
            ingredientId = outputIngredientId,
            areaId = outputAreaId,
            movementType = "PRODUCTION_OUTPUT",
            quantityBaseSigned = quantityBase.toPlainString(),
            unitCostBaseSnapshot = unitCostBase.toPlainString(),
            totalValueSnapshot = totalValue.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PRODUCTION_BATCH",
            sourceDocumentId = batchId,
            sourceOperationId = InventoryMovementOperationIds.productionOutput(batchId),
            sourceLineId = batchId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            productionBatches = snapshot.productionBatches + batch,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }
}
