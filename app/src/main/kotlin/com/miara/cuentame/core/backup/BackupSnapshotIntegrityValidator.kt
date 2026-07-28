package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.WasteReason
import java.math.BigDecimal

object BackupSnapshotIntegrityValidator {

    private val VALID_PURCHASE_STATUSES = DocumentStatus.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_STOCK_COUNT_STATUSES = StockCountStatus.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_STOCK_COUNT_AREA_STATUSES = CountAreaStatus.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_WASTE_STATUSES = DocumentStatus.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_WASTE_REASONS = WasteReason.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_MOVEMENT_TYPES = InventoryMovementType.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_SOURCE_DOC_TYPES = SourceDocumentType.entries.mapTo(mutableSetOf()) { it.name }
    private val VALID_UNIT_DIMENSIONS = UnitDimension.entries.mapTo(mutableSetOf()) { it.name }

    /**
     * Validates logical consistency, enum validity, document timestamps, movement graph semantics,
     * and restaurant isolation of a backup snapshot DTO.
     */
    fun validate(dto: BackupSnapshotDto, manifest: BackupManifest): Result<Unit> {
        val manifestRestaurantId = manifest.restaurantId ?: return Result.failure(Exception("Manifest missing restaurantId"))

        // 1. Restaurant consistency
        if (dto.restaurants.size != 1) {
            return Result.failure(Exception("Snapshot must contain exactly one restaurant"))
        }
        val restaurant = dto.restaurants[0]
        if (restaurant.id != manifestRestaurantId) return Result.failure(Exception("Snapshot restaurant ID mismatch"))
        if (restaurant.name != manifest.restaurantName) return Result.failure(Exception("Snapshot restaurant name mismatch"))
        if (restaurant.currencyCode != manifest.currencyCode) return Result.failure(Exception("Snapshot currencyCode mismatch"))
        if (restaurant.localeTag != manifest.localeTag) return Result.failure(Exception("Snapshot localeTag mismatch"))

        // 2. Primary Key Uniqueness & Non-blank ID checks across all 16 tables
        fun <T> checkUnique(list: List<T>, selector: (T) -> String, name: String): Result<Unit>? {
            val ids = list.map(selector)
            if (ids.any { it.isBlank() }) return Result.failure(Exception("Blank ID in table $name"))
            if (ids.distinct().size != ids.size) return Result.failure(Exception("Duplicate primary key in table $name"))
            return null
        }

        checkUnique(dto.inventoryAreas, { it.id }, "inventory_areas")?.let { return it }
        checkUnique(dto.ingredientCategories, { it.id }, "ingredient_categories")?.let { return it }
        checkUnique(dto.units, { it.id }, "units")?.let { return it }
        checkUnique(dto.ingredients, { it.id }, "ingredients")?.let { return it }
        checkUnique(dto.ingredientUnitOptions, { it.id }, "ingredient_unit_options")?.let { return it }
        checkUnique(dto.suppliers, { it.id }, "suppliers")?.let { return it }
        checkUnique(dto.purchaseReceipts, { it.id }, "purchase_receipts")?.let { return it }
        checkUnique(dto.purchaseLines, { it.id }, "purchase_lines")?.let { return it }
        checkUnique(dto.stockCounts, { it.id }, "stock_counts")?.let { return it }
        checkUnique(dto.stockCountAreas, { it.id }, "stock_count_areas")?.let { return it }
        checkUnique(dto.stockCountLines, { it.id }, "stock_count_lines")?.let { return it }
        checkUnique(dto.wasteEvents, { it.id }, "waste_events")?.let { return it }
        checkUnique(dto.inventoryMovements, { it.id }, "inventory_movements")?.let { return it }

        // Typed Composite Key Uniqueness for Projections
        val balanceKeys = dto.inventoryBalanceProjections.map { Triple(it.restaurantId, it.ingredientId, it.areaId) }
        if (balanceKeys.any { it.first.isBlank() || it.second.isBlank() || it.third.isBlank() }) {
            return Result.failure(Exception("Blank composite key field in inventory_balance_projections"))
        }
        if (balanceKeys.distinct().size != balanceKeys.size) {
            return Result.failure(Exception("Duplicate composite key in inventory_balance_projections"))
        }

        val costKeys = dto.ingredientCostProjections.map { Pair(it.restaurantId, it.ingredientId) }
        if (costKeys.any { it.first.isBlank() || it.second.isBlank() }) {
            return Result.failure(Exception("Blank composite key field in ingredient_cost_projections"))
        }
        if (costKeys.distinct().size != costKeys.size) {
            return Result.failure(Exception("Duplicate composite key in ingredient_cost_projections"))
        }

        // 3. Restaurant Ownership & Isolation (Direct & Transitive Resolution)
        if (dto.inventoryAreas.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in inventory_areas"))
        if (dto.ingredientCategories.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in ingredient_categories"))
        if (dto.ingredients.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in ingredients"))
        if (dto.suppliers.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in suppliers"))
        if (dto.purchaseReceipts.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in purchase_receipts"))
        if (dto.stockCounts.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in stock_counts"))
        if (dto.wasteEvents.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in waste_events"))
        if (dto.inventoryMovements.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in inventory_movements"))
        if (dto.inventoryBalanceProjections.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in inventory_balance_projections"))
        if (dto.ingredientCostProjections.any { it.restaurantId != manifestRestaurantId }) return Result.failure(Exception("Isolation error in ingredient_cost_projections"))

        // Lookup maps
        val unitById = dto.units.associateBy { it.id }
        val areaById = dto.inventoryAreas.associateBy { it.id }
        val catById = dto.ingredientCategories.associateBy { it.id }
        val ingById = dto.ingredients.associateBy { it.id }
        val optionById = dto.ingredientUnitOptions.associateBy { it.id }
        val supplierById = dto.suppliers.associateBy { it.id }
        val receiptById = dto.purchaseReceipts.associateBy { it.id }
        val purchaseLineById = dto.purchaseLines.associateBy { it.id }
        val countById = dto.stockCounts.associateBy { it.id }
        val countAreaById = dto.stockCountAreas.associateBy { it.id }
        val countLineById = dto.stockCountLines.associateBy { it.id }
        val wasteById = dto.wasteEvents.associateBy { it.id }
        val movementById = dto.inventoryMovements.associateBy { it.id }

        // Transitive restaurant ownership for child entities without explicit restaurantId
        if (dto.ingredientUnitOptions.any { option ->
                val parentIng = ingById[option.ingredientId]
                parentIng == null || parentIng.restaurantId != manifestRestaurantId
            }) {
            return Result.failure(Exception("Transitive isolation error in ingredient_unit_options"))
        }

        if (dto.purchaseLines.any { line ->
                val parentReceipt = receiptById[line.purchaseReceiptId]
                parentReceipt == null || parentReceipt.restaurantId != manifestRestaurantId
            }) {
            return Result.failure(Exception("Transitive isolation error in purchase_lines"))
        }

        if (dto.stockCountAreas.any { sca ->
                val parentCount = countById[sca.stockCountId]
                parentCount == null || parentCount.restaurantId != manifestRestaurantId
            }) {
            return Result.failure(Exception("Transitive isolation error in stock_count_areas"))
        }

        if (dto.stockCountLines.any { line ->
                val parentCountArea = countAreaById[line.stockCountAreaId]
                val parentCount = parentCountArea?.let { countById[it.stockCountId] }
                parentCount == null || parentCount.restaurantId != manifestRestaurantId
            }) {
            return Result.failure(Exception("Transitive isolation error in stock_count_lines"))
        }

        // 4. Relational & Foreign Key Integrity
        for (unit in dto.units) {
            if (unit.dimension !in VALID_UNIT_DIMENSIONS) return Result.failure(Exception("Invalid unit dimension: ${unit.dimension}"))
        }

        for (ing in dto.ingredients) {
            if (!unitById.containsKey(ing.baseUnitId)) return Result.failure(Exception("Broken FK: ingredient to unit"))
            if (ing.categoryId != null && !catById.containsKey(ing.categoryId)) return Result.failure(Exception("Broken FK: ingredient to category"))
            if (ing.defaultAreaId != null && !areaById.containsKey(ing.defaultAreaId)) return Result.failure(Exception("Broken FK: ingredient to area"))
        }

        for (opt in dto.ingredientUnitOptions) {
            if (!ingById.containsKey(opt.ingredientId)) return Result.failure(Exception("Broken FK: unit_option to ingredient"))
            if (opt.standardUnitId != null && !unitById.containsKey(opt.standardUnitId)) return Result.failure(Exception("Broken FK: unit_option to unit"))
        }

        for (receipt in dto.purchaseReceipts) {
            if (receipt.supplierId != null && !supplierById.containsKey(receipt.supplierId)) {
                return Result.failure(Exception("Broken FK: purchase_receipt to supplier"))
            }
        }

        for (line in dto.purchaseLines) {
            if (!receiptById.containsKey(line.purchaseReceiptId)) return Result.failure(Exception("Broken FK: purchase_line to receipt"))
            if (!ingById.containsKey(line.ingredientId)) return Result.failure(Exception("Broken FK: purchase_line to ingredient"))
            if (!areaById.containsKey(line.areaId)) return Result.failure(Exception("Broken FK: purchase_line to area"))
            val option = optionById[line.ingredientUnitOptionId] ?: return Result.failure(Exception("Broken FK: purchase_line to unit_option"))
            if (option.ingredientId != line.ingredientId) {
                return Result.failure(Exception("Unit option ingredient mismatch in purchase_lines"))
            }
        }

        for (sca in dto.stockCountAreas) {
            if (!countById.containsKey(sca.stockCountId)) return Result.failure(Exception("Broken FK: stock_count_area to stock_count"))
            if (!areaById.containsKey(sca.areaId)) return Result.failure(Exception("Broken FK: stock_count_area to area"))
        }

        for (scl in dto.stockCountLines) {
            if (!countAreaById.containsKey(scl.stockCountAreaId)) return Result.failure(Exception("Broken FK: stock_count_line to stock_count_area"))
            if (!ingById.containsKey(scl.ingredientId)) return Result.failure(Exception("Broken FK: stock_count_line to ingredient"))
            val option = optionById[scl.ingredientUnitOptionId] ?: return Result.failure(Exception("Broken FK: stock_count_line to unit_option"))
            if (option.ingredientId != scl.ingredientId) {
                return Result.failure(Exception("Unit option ingredient mismatch in stock_count_lines"))
            }
        }

        for (waste in dto.wasteEvents) {
            if (!ingById.containsKey(waste.ingredientId)) return Result.failure(Exception("Broken FK: waste_event to ingredient"))
            if (!areaById.containsKey(waste.areaId)) return Result.failure(Exception("Broken FK: waste_event to area"))
            val option = optionById[waste.ingredientUnitOptionId] ?: return Result.failure(Exception("Broken FK: waste_event to unit_option"))
            if (option.ingredientId != waste.ingredientId) {
                return Result.failure(Exception("Unit option ingredient mismatch in waste_events"))
            }
            if (waste.reason !in VALID_WASTE_REASONS) {
                return Result.failure(Exception("Invalid waste reason: ${waste.reason}"))
            }
        }

        for (bal in dto.inventoryBalanceProjections) {
            if (!ingById.containsKey(bal.ingredientId)) return Result.failure(Exception("Broken FK: balance_projection to ingredient"))
            if (!areaById.containsKey(bal.areaId)) return Result.failure(Exception("Broken FK: balance_projection to area"))
        }

        for (cost in dto.ingredientCostProjections) {
            if (!ingById.containsKey(cost.ingredientId)) return Result.failure(Exception("Broken FK: cost_projection to ingredient"))
        }

        // 5. Document Semantics & Timestamps
        for (receipt in dto.purchaseReceipts) {
            if (receipt.status !in VALID_PURCHASE_STATUSES) return Result.failure(Exception("Invalid purchase receipt status"))
            if (receipt.createdAt > receipt.updatedAt) return Result.failure(Exception("Purchase receipt createdAt > updatedAt"))
            when (receipt.status) {
                DocumentStatus.DRAFT.name -> {
                    if (receipt.postedAt != null || receipt.voidedAt != null) {
                        return Result.failure(Exception("DRAFT purchase receipt must not have postedAt or voidedAt"))
                    }
                }
                DocumentStatus.POSTED.name -> {
                    if (receipt.postedAt == null) return Result.failure(Exception("POSTED purchase receipt requires postedAt"))
                    if (receipt.voidedAt != null) return Result.failure(Exception("POSTED purchase receipt must not have voidedAt"))
                }
                DocumentStatus.VOIDED.name -> {
                    if (receipt.postedAt == null || receipt.voidedAt == null) {
                        return Result.failure(Exception("VOIDED purchase receipt requires both postedAt and voidedAt"))
                    }
                    if (receipt.postedAt > receipt.voidedAt) {
                        return Result.failure(Exception("Purchase receipt postedAt must be <= voidedAt"))
                    }
                }
            }
        }

        for (waste in dto.wasteEvents) {
            if (waste.status !in VALID_WASTE_STATUSES) return Result.failure(Exception("Invalid waste event status"))
            if (waste.createdAt > waste.updatedAt) return Result.failure(Exception("Waste event createdAt > updatedAt"))
            when (waste.status) {
                DocumentStatus.DRAFT.name -> {
                    if (waste.postedAt != null || waste.voidedAt != null) {
                        return Result.failure(Exception("DRAFT waste event must not have postedAt or voidedAt"))
                    }
                }
                DocumentStatus.POSTED.name -> {
                    if (waste.postedAt == null) return Result.failure(Exception("POSTED waste event requires postedAt"))
                    if (waste.voidedAt != null) return Result.failure(Exception("POSTED waste event must not have voidedAt"))
                }
                DocumentStatus.VOIDED.name -> {
                    if (waste.postedAt == null || waste.voidedAt == null) {
                        return Result.failure(Exception("VOIDED waste event requires both postedAt and voidedAt"))
                    }
                    if (waste.postedAt > waste.voidedAt) {
                        return Result.failure(Exception("Waste event postedAt must be <= voidedAt"))
                    }
                }
            }
        }

        for (count in dto.stockCounts) {
            if (count.status !in VALID_STOCK_COUNT_STATUSES) return Result.failure(Exception("Invalid stock count status"))
            if (count.createdAt > count.updatedAt) return Result.failure(Exception("Stock count createdAt > updatedAt"))
            when (count.status) {
                StockCountStatus.DRAFT.name -> {
                    if (count.completedAt != null || count.voidedAt != null) {
                        return Result.failure(Exception("DRAFT stock count must not have completedAt or voidedAt"))
                    }
                }
                StockCountStatus.COMPLETED.name -> {
                    if (count.completedAt == null) return Result.failure(Exception("COMPLETED stock count requires completedAt"))
                    if (count.voidedAt != null) return Result.failure(Exception("COMPLETED stock count must not have voidedAt"))
                }
                StockCountStatus.VOIDED.name -> {
                    if (count.completedAt == null || count.voidedAt == null) {
                        return Result.failure(Exception("VOIDED stock count requires both completedAt and voidedAt"))
                    }
                    if (count.completedAt > count.voidedAt) {
                        return Result.failure(Exception("Stock count completedAt must be <= voidedAt"))
                    }
                }
            }
        }

        for (sca in dto.stockCountAreas) {
            if (sca.status !in VALID_STOCK_COUNT_AREA_STATUSES) return Result.failure(Exception("Invalid stock count area status"))
            if (sca.status == CountAreaStatus.COMPLETED.name && sca.completedAt == null) {
                return Result.failure(Exception("COMPLETED stock count area requires completedAt"))
            }
            if (sca.status != CountAreaStatus.COMPLETED.name && sca.completedAt != null) {
                return Result.failure(Exception("Non-COMPLETED stock count area must not have completedAt"))
            }
        }

        // 6. Movement Graph Semantics Validation
        val reversedMovementIds = mutableSetOf<String>()

        for (move in dto.inventoryMovements) {
            if (move.movementType !in VALID_MOVEMENT_TYPES) {
                return Result.failure(Exception("Invalid inventory movement type: ${move.movementType}"))
            }
            if (move.sourceDocumentType !in VALID_SOURCE_DOC_TYPES) {
                return Result.failure(Exception("Invalid source document type: ${move.sourceDocumentType}"))
            }
            if (move.sourceDocumentId.isBlank()) {
                return Result.failure(Exception("Blank sourceDocumentId in inventory_movements"))
            }
            if (move.sourceOperationId.isBlank()) {
                return Result.failure(Exception("Blank sourceOperationId in inventory_movements"))
            }

            val ing = ingById[move.ingredientId] ?: return Result.failure(Exception("Broken FK: movement to ingredient"))
            val area = areaById[move.areaId] ?: return Result.failure(Exception("Broken FK: movement to area"))
            if (ing.restaurantId != manifestRestaurantId || area.restaurantId != manifestRestaurantId) {
                return Result.failure(Exception("Restaurant mismatch on movement ingredient/area"))
            }

            when (move.movementType) {
                InventoryMovementType.PURCHASE.name -> {
                    if (move.sourceDocumentType != SourceDocumentType.PURCHASE_RECEIPT.name) {
                        return Result.failure(Exception("PURCHASE movement must use PURCHASE_RECEIPT source document type"))
                    }
                    val receipt = receiptById[move.sourceDocumentId]
                        ?: return Result.failure(Exception("PURCHASE movement sourceDocumentId not found in purchase_receipts"))
                    val lineId = move.sourceLineId
                        ?: return Result.failure(Exception("PURCHASE movement requires non-null sourceLineId"))
                    val line = purchaseLineById[lineId]
                        ?: return Result.failure(Exception("PURCHASE movement sourceLineId not found in purchase_lines"))
                    if (line.purchaseReceiptId != receipt.id) {
                        return Result.failure(Exception("PURCHASE movement sourceLineId does not belong to source purchase receipt"))
                    }
                    if (line.ingredientId != move.ingredientId || line.areaId != move.areaId) {
                        return Result.failure(Exception("PURCHASE movement ingredient/area mismatch with purchase line"))
                    }
                }
                InventoryMovementType.WASTE.name -> {
                    if (move.sourceDocumentType != SourceDocumentType.WASTE_EVENT.name) {
                        return Result.failure(Exception("WASTE movement must use WASTE_EVENT source document type"))
                    }
                    val waste = wasteById[move.sourceDocumentId]
                        ?: return Result.failure(Exception("WASTE movement sourceDocumentId not found in waste_events"))
                    if (waste.ingredientId != move.ingredientId || waste.areaId != move.areaId) {
                        return Result.failure(Exception("WASTE movement ingredient/area mismatch with waste event"))
                    }
                }
                InventoryMovementType.OPENING_BALANCE.name, InventoryMovementType.COUNT_ADJUSTMENT.name -> {
                    if (move.sourceDocumentType != SourceDocumentType.STOCK_COUNT.name) {
                        return Result.failure(Exception("${move.movementType} movement must use STOCK_COUNT source document type"))
                    }
                    val count = countById[move.sourceDocumentId]
                        ?: return Result.failure(Exception("${move.movementType} movement sourceDocumentId not found in stock_counts"))
                    val lineId = move.sourceLineId
                        ?: return Result.failure(Exception("${move.movementType} movement requires non-null sourceLineId"))
                    val line = countLineById[lineId]
                        ?: return Result.failure(Exception("${move.movementType} movement sourceLineId not found in stock_count_lines"))
                    val sca = countAreaById[line.stockCountAreaId]
                        ?: return Result.failure(Exception("Stock count line parent area not found"))
                    if (sca.stockCountId != count.id) {
                        return Result.failure(Exception("${move.movementType} movement line does not belong to source stock count"))
                    }
                    if (line.ingredientId != move.ingredientId || sca.areaId != move.areaId) {
                        return Result.failure(Exception("${move.movementType} movement ingredient/area mismatch with stock count line"))
                    }
                }
                InventoryMovementType.MANUAL_ADJUSTMENT.name -> {
                    if (move.sourceDocumentType != SourceDocumentType.MANUAL.name) {
                        return Result.failure(Exception("MANUAL_ADJUSTMENT movement must use MANUAL source document type"))
                    }
                }
                InventoryMovementType.REVERSAL.name -> {
                    val targetId = move.reversalOfMovementId
                        ?: return Result.failure(Exception("REVERSAL movement requires non-null reversalOfMovementId"))
                    if (targetId == move.id) {
                        return Result.failure(Exception("REVERSAL movement cannot reverse itself"))
                    }
                    val original = movementById[targetId]
                        ?: return Result.failure(Exception("REVERSAL movement target not found in inventory_movements"))
                    if (original.movementType == InventoryMovementType.REVERSAL.name) {
                        return Result.failure(Exception("REVERSAL movement cannot point to another REVERSAL movement"))
                    }
                    if (reversedMovementIds.contains(targetId)) {
                        return Result.failure(Exception("Multiple REVERSAL movements pointing to the same original movement ($targetId)"))
                    }
                    reversedMovementIds.add(targetId)

                    if (move.restaurantId != original.restaurantId ||
                        move.ingredientId != original.ingredientId ||
                        move.areaId != original.areaId) {
                        return Result.failure(Exception("REVERSAL movement restaurant/ingredient/area mismatch with original"))
                    }

                    try {
                        val revQty = BigDecimal(move.quantityBaseSigned)
                        val origQty = BigDecimal(original.quantityBaseSigned)
                        if (revQty.add(origQty).compareTo(BigDecimal.ZERO) != 0) {
                            return Result.failure(Exception("REVERSAL movement quantity is not the exact negation of original"))
                        }

                        if (move.totalValueSnapshot != null && original.totalValueSnapshot != null) {
                            val revVal = BigDecimal(move.totalValueSnapshot)
                            val origVal = BigDecimal(original.totalValueSnapshot)
                            if (revVal.add(origVal).compareTo(BigDecimal.ZERO) != 0) {
                                return Result.failure(Exception("REVERSAL movement totalValueSnapshot is not the exact negation of original"))
                            }
                        }
                    } catch (e: Exception) {
                        return Result.failure(Exception("Invalid decimal format during REVERSAL quantity/value verification"))
                    }
                }
            }
        }

        // 7. Decimal & Domain Value Range Validation
        fun checkDecimal(valStr: String?, tableName: String, fieldName: String): BigDecimal? {
            if (valStr == null) return null
            return try {
                BigDecimal(valStr)
            } catch (e: Exception) {
                throw Exception("Invalid decimal format in $tableName.$fieldName")
            }
        }

        try {
            dto.units.forEach { unit ->
                val factor = checkDecimal(unit.factorToCanonical, "units", "factorToCanonical")
                if (factor != null && factor <= BigDecimal.ZERO) {
                    return Result.failure(Exception("unit factorToCanonical must be > 0"))
                }
            }

            dto.ingredients.forEach { ing ->
                val point = checkDecimal(ing.reorderPointBase, "ingredients", "reorderPointBase")
                if (point != null && point < BigDecimal.ZERO) {
                    return Result.failure(Exception("ingredient reorderPointBase must be >= 0"))
                }
            }

            dto.ingredientUnitOptions.forEach { opt ->
                val factor = checkDecimal(opt.factorToBase, "ingredient_unit_options", "factorToBase")
                if (factor != null && factor <= BigDecimal.ZERO) {
                    return Result.failure(Exception("unit option factorToBase must be > 0"))
                }
            }

            dto.purchaseLines.forEach { line ->
                val qEntered = checkDecimal(line.quantityEntered, "purchase_lines", "quantityEntered")
                val qBase = checkDecimal(line.quantityBase, "purchase_lines", "quantityBase")
                val lTotal = checkDecimal(line.lineTotal, "purchase_lines", "lineTotal")
                checkDecimal(line.unitCostBase, "purchase_lines", "unitCostBase")

                if (qEntered != null && qEntered <= BigDecimal.ZERO) return Result.failure(Exception("purchase_line quantityEntered must be > 0"))
                if (qBase != null && qBase <= BigDecimal.ZERO) return Result.failure(Exception("purchase_line quantityBase must be > 0"))
                if (lTotal != null && lTotal < BigDecimal.ZERO) return Result.failure(Exception("purchase_line lineTotal must be >= 0"))
            }

            dto.stockCountLines.forEach { line ->
                val qEntered = checkDecimal(line.quantityEntered, "stock_count_lines", "quantityEntered")
                val qBase = checkDecimal(line.quantityBase, "stock_count_lines", "quantityBase")
                checkDecimal(line.expectedQuantityBaseSnapshot, "stock_count_lines", "expectedQuantityBaseSnapshot")
                checkDecimal(line.adjustmentQuantityBase, "stock_count_lines", "adjustmentQuantityBase")

                if (qEntered != null && qEntered < BigDecimal.ZERO) return Result.failure(Exception("stock_count_line quantityEntered must be >= 0"))
                if (qBase != null && qBase < BigDecimal.ZERO) return Result.failure(Exception("stock_count_line quantityBase must be >= 0"))
            }

            dto.wasteEvents.forEach { waste ->
                val qEntered = checkDecimal(waste.quantityEntered, "waste_events", "quantityEntered")
                val qBase = checkDecimal(waste.quantityBase, "waste_events", "quantityBase")

                if (qEntered != null && qEntered <= BigDecimal.ZERO) return Result.failure(Exception("waste_event quantityEntered must be > 0"))
                if (qBase != null && qBase <= BigDecimal.ZERO) return Result.failure(Exception("waste_event quantityBase must be > 0"))
            }

            dto.inventoryMovements.forEach { move ->
                checkDecimal(move.quantityBaseSigned, "inventory_movements", "quantityBaseSigned")
                checkDecimal(move.unitCostBaseSnapshot, "inventory_movements", "unitCostBaseSnapshot")
                checkDecimal(move.totalValueSnapshot, "inventory_movements", "totalValueSnapshot")
            }

            dto.inventoryBalanceProjections.forEach { checkDecimal(it.quantityBase, "inventory_balance_projections", "quantityBase") }
            dto.ingredientCostProjections.forEach { checkDecimal(it.averageUnitCostBase, "ingredient_cost_projections", "averageUnitCostBase") }

        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(Unit)
    }
}
