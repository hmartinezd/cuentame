package com.miara.cuentame.core.backup

import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.database.backup.BackupSnapshot
import com.miara.cuentame.core.model.inventory.*
import java.math.BigDecimal

object BackupTestFixtures {

    const val RESTAURANT_ID = "rest-1"

    fun createPostedLifecycleSnapshot(
        restaurantId: String = RESTAURANT_ID,
        purchaseAttPath: String? = null,
        wasteAttPath: String? = null
    ): BackupSnapshot {
        val rest = RestaurantEntity(
            id = restaurantId,
            name = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val area1 = InventoryAreaEntity(
            id = "area-1",
            restaurantId = restaurantId,
            name = "Kitchen",
            normalizedName = "kitchen",
            sortOrder = 1,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val area2 = InventoryAreaEntity(
            id = "area-2",
            restaurantId = restaurantId,
            name = "Storage",
            normalizedName = "storage",
            sortOrder = 2,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val cat1 = IngredientCategoryEntity(
            id = "cat-1",
            restaurantId = restaurantId,
            name = "Produce",
            normalizedName = "produce",
            sortOrder = 1,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val unit1 = UnitEntity(
            id = "u-1",
            name = "Kilogram",
            symbol = "kg",
            dimension = UnitDimension.MASS.name,
            factorToCanonical = BigDecimal.ONE,
            isSystem = true,
            sortOrder = 1
        )

        val ing1 = IngredientEntity(
            id = "ing-1",
            restaurantId = restaurantId,
            name = "Tomato",
            normalizedName = "tomato",
            categoryId = cat1.id,
            baseUnitId = unit1.id,
            defaultAreaId = area1.id,
            sku = "SKU-TOM",
            notes = "Fresh Red Tomatoes",
            reorderPointBase = BigDecimal("10.0"),
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val ing2 = IngredientEntity(
            id = "ing-2",
            restaurantId = restaurantId,
            name = "Onion",
            normalizedName = "onion",
            categoryId = cat1.id,
            baseUnitId = unit1.id,
            defaultAreaId = area2.id,
            sku = "SKU-ONI",
            notes = "Yellow Onions",
            reorderPointBase = BigDecimal("5.0"),
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val opt1 = IngredientUnitOptionEntity(
            id = "opt-1",
            ingredientId = ing1.id,
            displayName = "1kg Bag",
            shortLabel = "kg",
            standardUnitId = unit1.id,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val opt2 = IngredientUnitOptionEntity(
            id = "opt-2",
            ingredientId = ing2.id,
            displayName = "1kg Bag",
            shortLabel = "kg",
            standardUnitId = unit1.id,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val supplier1 = SupplierEntity(
            id = "sup-1",
            restaurantId = restaurantId,
            name = "Farm Fresh Co",
            normalizedName = "farm fresh co",
            phone = "555-0100",
            email = "farm@example.com",
            notes = "Local organic vendor",
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )

        val receipt1 = PurchaseReceiptEntity(
            id = "pr-1",
            restaurantId = restaurantId,
            supplierId = supplier1.id,
            invoiceNumber = "INV-1001",
            purchaseDate = 1500L,
            status = DocumentStatus.POSTED.name,
            notes = "Weekly delivery",
            attachmentPath = purchaseAttPath,
            createdAt = 1000L,
            updatedAt = 2000L,
            postedAt = 1500L,
            voidedAt = null
        )

        val line1 = PurchaseLineEntity(
            id = "pl-1",
            purchaseReceiptId = receipt1.id,
            ingredientId = ing1.id,
            areaId = area1.id,
            ingredientUnitOptionId = opt1.id,
            quantityEntered = "10.0",
            quantityBase = "10.0",
            unitCostBase = "2.50",
            lineTotal = "25.00",
            notes = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val sc1 = StockCountEntity(
            id = "sc-1",
            restaurantId = restaurantId,
            name = "Monthly Count",
            startedAt = 1000L,
            effectiveAt = 1600L,
            completedAt = 1600L,
            status = StockCountStatus.COMPLETED.name,
            notes = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            voidedAt = null
        )

        val sca1 = StockCountAreaEntity(
            id = "sca-1",
            stockCountId = sc1.id,
            areaId = area1.id,
            status = CountAreaStatus.COMPLETED.name,
            startedAt = 1000L,
            completedAt = 1600L,
            sortOrder = 1
        )

        val scl1 = StockCountLineEntity(
            id = "scl-1",
            stockCountAreaId = sca1.id,
            ingredientId = ing1.id,
            ingredientUnitOptionId = opt1.id,
            quantityEntered = "9.5",
            quantityBase = "9.5",
            expectedQuantityBaseSnapshot = "10.0",
            adjustmentQuantityBase = "-0.5",
            notes = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val waste1 = WasteEventEntity(
            id = "we-1",
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            areaId = area1.id,
            ingredientUnitOptionId = opt1.id,
            quantityEntered = "0.5",
            quantityBase = "0.5",
            reason = WasteReason.SPOILED.name,
            effectiveAt = 1550L,
            notes = "Overripe",
            attachmentPath = wasteAttPath,
            status = DocumentStatus.POSTED.name,
            createdAt = 1000L,
            updatedAt = 2000L,
            postedAt = 1550L,
            voidedAt = null
        )

        val movePurchase = InventoryMovementEntity(
            id = "m-1",
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            areaId = area1.id,
            movementType = InventoryMovementType.PURCHASE.name,
            quantityBaseSigned = "10.0",
            unitCostBaseSnapshot = "2.50",
            totalValueSnapshot = "25.00",
            effectiveAt = 1500L,
            sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
            sourceDocumentId = receipt1.id,
            sourceOperationId = "purchase-post:${receipt1.id}:${line1.id}",
            sourceLineId = line1.id,
            reversalOfMovementId = null,
            createdAt = 1500L
        )

        val moveWaste = InventoryMovementEntity(
            id = "m-2",
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            areaId = area1.id,
            movementType = InventoryMovementType.WASTE.name,
            quantityBaseSigned = "-0.5",
            unitCostBaseSnapshot = "2.50",
            totalValueSnapshot = "-1.25",
            effectiveAt = 1550L,
            sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
            sourceDocumentId = waste1.id,
            sourceOperationId = "waste-post:${waste1.id}",
            sourceLineId = waste1.id,
            reversalOfMovementId = null,
            createdAt = 1550L
        )

        val moveCount = InventoryMovementEntity(
            id = "m-3",
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            areaId = area1.id,
            movementType = InventoryMovementType.COUNT_ADJUSTMENT.name,
            quantityBaseSigned = "-0.5",
            unitCostBaseSnapshot = "2.50",
            totalValueSnapshot = "-1.25",
            effectiveAt = 1600L,
            sourceDocumentType = SourceDocumentType.STOCK_COUNT.name,
            sourceDocumentId = sc1.id,
            sourceOperationId = "stock-count-complete:${sc1.id}:${scl1.id}",
            sourceLineId = scl1.id,
            reversalOfMovementId = null,
            createdAt = 1600L
        )

        val bal1 = InventoryBalanceProjectionEntity(
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            areaId = area1.id,
            quantityBase = "9.0",
            updatedAt = 2000L
        )

        val cost1 = IngredientCostProjectionEntity(
            restaurantId = restaurantId,
            ingredientId = ing1.id,
            averageUnitCostBase = "2.50",
            updatedAt = 2000L
        )

        val cost2 = IngredientCostProjectionEntity(
            restaurantId = restaurantId,
            ingredientId = ing2.id,
            averageUnitCostBase = null,
            updatedAt = 2000L
        )

        return BackupSnapshot(
            restaurants = listOf(rest),
            inventoryAreas = listOf(area1, area2),
            ingredientCategories = listOf(cat1),
            units = listOf(unit1),
            ingredients = listOf(ing1, ing2),
            ingredientUnitOptions = listOf(opt1, opt2),
            suppliers = listOf(supplier1),
            purchaseReceipts = listOf(receipt1),
            purchaseLines = listOf(line1),
            stockCounts = listOf(sc1),
            stockCountAreas = listOf(sca1),
            stockCountLines = listOf(scl1),
            wasteEvents = listOf(waste1),
            inventoryMovements = listOf(movePurchase, moveWaste, moveCount),
            inventoryBalanceProjections = listOf(bal1),
            ingredientCostProjections = listOf(cost1, cost2)
        )
    }

    fun createVoidedLifecycleSnapshot(
        restaurantId: String = RESTAURANT_ID
    ): BackupSnapshot {
        val base = createPostedLifecycleSnapshot(restaurantId)

        val voidedWaste = base.wasteEvents[0].copy(
            status = DocumentStatus.VOIDED.name,
            voidedAt = 1700L
        )

        val moveReversal = InventoryMovementEntity(
            id = "m-4",
            restaurantId = restaurantId,
            ingredientId = base.ingredients[0].id,
            areaId = base.inventoryAreas[0].id,
            movementType = InventoryMovementType.REVERSAL.name,
            quantityBaseSigned = "0.5",
            unitCostBaseSnapshot = "2.50",
            totalValueSnapshot = "1.25",
            effectiveAt = 1700L,
            sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
            sourceDocumentId = voidedWaste.id,
            sourceOperationId = "reversal:${base.inventoryMovements[1].id}",
            sourceLineId = voidedWaste.id,
            reversalOfMovementId = base.inventoryMovements[1].id,
            createdAt = 1700L
        )

        val bal1 = InventoryBalanceProjectionEntity(
            restaurantId = restaurantId,
            ingredientId = base.ingredients[0].id,
            areaId = base.inventoryAreas[0].id,
            quantityBase = "9.5",
            updatedAt = 2000L
        )

        return base.copy(
            wasteEvents = listOf(voidedWaste),
            inventoryMovements = base.inventoryMovements + moveReversal,
            inventoryBalanceProjections = listOf(bal1)
        )
    }

    fun createValidSnapshot(
        restaurantId: String = RESTAURANT_ID,
        purchaseAttPath: String? = null,
        wasteAttPath: String? = null
    ): BackupSnapshot = createPostedLifecycleSnapshot(restaurantId, purchaseAttPath, wasteAttPath)

    fun createEmptySnapshotDto(): com.miara.cuentame.core.backup.model.BackupSnapshotDto {
        return com.miara.cuentame.core.backup.model.BackupSnapshotDto(
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
            ingredientCostProjections = emptyList()
        )
    }
}
