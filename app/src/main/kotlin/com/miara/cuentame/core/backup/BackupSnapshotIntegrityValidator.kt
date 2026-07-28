package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.inventory.*
import java.math.BigDecimal
import java.math.RoundingMode

object BackupSnapshotIntegrityValidator {

    /**
     * Validates logical consistency, enum validity, document timestamps, movement graph semantics,
     * document lifecycle consistency, numeric semantics, and restaurant isolation.
     */
    fun validate(dto: BackupSnapshotDto, manifest: BackupManifest): Result<Unit> {
        val manifestRestaurantId = manifest.restaurantId
            ?: return Result.failure(Exception("Manifest missing restaurantId"))

        // 1. Restaurant consistency
        if (dto.restaurants.size != 1) {
            return Result.failure(Exception("Snapshot must contain exactly one restaurant"))
        }
        val restaurant = dto.restaurants[0]
        if (restaurant.id != manifestRestaurantId) return Result.failure(Exception("Snapshot restaurant ID mismatch"))
        if (restaurant.name != manifest.restaurantName) return Result.failure(Exception("Snapshot restaurant name mismatch"))
        if (restaurant.currencyCode != manifest.currencyCode) return Result.failure(Exception("Snapshot currencyCode mismatch"))
        if (restaurant.localeTag != manifest.localeTag) return Result.failure(Exception("Snapshot localeTag mismatch"))

        // 2. Primary Key Uniqueness & Non-blank ID checks
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

        // 3. Restaurant Ownership & Isolation
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

        // Transitive restaurant ownership
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

        // 4. Exhaustive Enum Validation & Relational Integrity
        fun checkDecimal(valStr: String?, tableName: String, fieldName: String): BigDecimal? {
            if (valStr == null) return null
            return try {
                BigDecimal(valStr)
            } catch (e: Exception) {
                throw Exception("Invalid decimal format in $tableName.$fieldName")
            }
        }

        try {
            for (unit in dto.units) {
                val dim = try {
                    UnitDimension.valueOf(unit.dimension)
                } catch (e: IllegalArgumentException) {
                    return Result.failure(Exception("Invalid unit dimension"))
                }
                when (dim) {
                    UnitDimension.MASS, UnitDimension.VOLUME, UnitDimension.COUNT -> {}
                }
                val factor = checkDecimal(unit.factorToCanonical, "units", "factorToCanonical")
                if (factor != null && factor <= BigDecimal.ZERO) {
                    return Result.failure(Exception("unit factorToCanonical must be > 0"))
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
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
            val reason = try {
                WasteReason.valueOf(waste.reason)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid waste reason"))
            }
            when (reason) {
                WasteReason.EXPIRED, WasteReason.SPOILED, WasteReason.PREPARATION_ERROR,
                WasteReason.OVERPRODUCTION, WasteReason.DROPPED_OR_DAMAGED,
                WasteReason.CUSTOMER_RETURN, WasteReason.QUALITY_REJECTION, WasteReason.OTHER -> {}
            }
        }

        // 5. Document Semantics & Timestamps with Exhaustive Enums
        for (receipt in dto.purchaseReceipts) {
            val status = try {
                DocumentStatus.valueOf(receipt.status)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid purchase receipt status"))
            }
            if (receipt.createdAt > receipt.updatedAt) return Result.failure(Exception("Purchase receipt createdAt > updatedAt"))
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (receipt.postedAt != null || receipt.voidedAt != null) {
                        return Result.failure(Exception("DRAFT purchase receipt must not have postedAt or voidedAt"))
                    }
                }
                DocumentStatus.POSTED -> {
                    if (receipt.postedAt == null) return Result.failure(Exception("POSTED purchase receipt requires postedAt"))
                    if (receipt.voidedAt != null) return Result.failure(Exception("POSTED purchase receipt must not have voidedAt"))
                }
                DocumentStatus.VOIDED -> {
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
            val status = try {
                DocumentStatus.valueOf(waste.status)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid waste event status"))
            }
            if (waste.createdAt > waste.updatedAt) return Result.failure(Exception("Waste event createdAt > updatedAt"))
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (waste.postedAt != null || waste.voidedAt != null) {
                        return Result.failure(Exception("DRAFT waste event must not have postedAt or voidedAt"))
                    }
                }
                DocumentStatus.POSTED -> {
                    if (waste.postedAt == null) return Result.failure(Exception("POSTED waste event requires postedAt"))
                    if (waste.voidedAt != null) return Result.failure(Exception("POSTED waste event must not have voidedAt"))
                }
                DocumentStatus.VOIDED -> {
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
            val status = try {
                StockCountStatus.valueOf(count.status)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid stock count status"))
            }
            if (count.createdAt > count.updatedAt) return Result.failure(Exception("Stock count createdAt > updatedAt"))
            when (status) {
                StockCountStatus.DRAFT -> {
                    if (count.completedAt != null || count.voidedAt != null) {
                        return Result.failure(Exception("DRAFT stock count must not have completedAt or voidedAt"))
                    }
                }
                StockCountStatus.COMPLETED -> {
                    if (count.completedAt == null) return Result.failure(Exception("COMPLETED stock count requires completedAt"))
                    if (count.voidedAt != null) return Result.failure(Exception("COMPLETED stock count must not have voidedAt"))
                }
                StockCountStatus.VOIDED -> {
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
            val status = try {
                CountAreaStatus.valueOf(sca.status)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid stock count area status"))
            }
            when (status) {
                CountAreaStatus.NOT_STARTED, CountAreaStatus.IN_PROGRESS -> {
                    if (sca.completedAt != null) return Result.failure(Exception("Non-COMPLETED stock count area must not have completedAt"))
                }
                CountAreaStatus.COMPLETED -> {
                    if (sca.completedAt == null) return Result.failure(Exception("COMPLETED stock count area requires completedAt"))
                }
            }
        }

        // 6. Movement Unique Key & Exhaustive Enums Check
        val moveKeys = dto.inventoryMovements.map { Triple(it.sourceDocumentType, it.sourceDocumentId, it.sourceOperationId) }
        if (moveKeys.distinct().size != moveKeys.size) {
            return Result.failure(Exception("Duplicate source operation key in inventory_movements"))
        }

        val reversedMovementIds = mutableSetOf<String>()

        for (move in dto.inventoryMovements) {
            val moveType = try {
                InventoryMovementType.valueOf(move.movementType)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid inventory movement type"))
            }

            val docType = try {
                SourceDocumentType.valueOf(move.sourceDocumentType)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Invalid source document type"))
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

            val moveQty = try { BigDecimal(move.quantityBaseSigned) } catch (e: Exception) {
                return Result.failure(Exception("Invalid quantityBaseSigned decimal format"))
            }

            when (moveType) {
                InventoryMovementType.PURCHASE -> {
                    if (docType != SourceDocumentType.PURCHASE_RECEIPT) {
                        return Result.failure(Exception("PURCHASE movement must use PURCHASE_RECEIPT source document type"))
                    }
                    if (moveQty <= BigDecimal.ZERO) {
                        return Result.failure(Exception("PURCHASE movement quantity must be > 0"))
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
                InventoryMovementType.WASTE -> {
                    if (docType != SourceDocumentType.WASTE_EVENT) {
                        return Result.failure(Exception("WASTE movement must use WASTE_EVENT source document type"))
                    }
                    if (moveQty >= BigDecimal.ZERO) {
                        return Result.failure(Exception("WASTE movement quantity must be < 0"))
                    }
                    val waste = wasteById[move.sourceDocumentId]
                        ?: return Result.failure(Exception("WASTE movement sourceDocumentId not found in waste_events"))
                    if (waste.ingredientId != move.ingredientId || waste.areaId != move.areaId) {
                        return Result.failure(Exception("WASTE movement ingredient/area mismatch with waste event"))
                    }
                }
                InventoryMovementType.OPENING_BALANCE, InventoryMovementType.COUNT_ADJUSTMENT -> {
                    if (docType != SourceDocumentType.STOCK_COUNT) {
                        return Result.failure(Exception("${moveType.name} movement must use STOCK_COUNT source document type"))
                    }
                    val count = countById[move.sourceDocumentId]
                        ?: return Result.failure(Exception("${moveType.name} movement sourceDocumentId not found in stock_counts"))
                    val lineId = move.sourceLineId
                        ?: return Result.failure(Exception("${moveType.name} movement requires non-null sourceLineId"))
                    val line = countLineById[lineId]
                        ?: return Result.failure(Exception("${moveType.name} movement sourceLineId not found in stock_count_lines"))
                    val sca = countAreaById[line.stockCountAreaId]
                        ?: return Result.failure(Exception("Stock count line parent area not found"))
                    if (sca.stockCountId != count.id) {
                        return Result.failure(Exception("${moveType.name} movement line does not belong to source stock count"))
                    }
                    if (line.ingredientId != move.ingredientId || sca.areaId != move.areaId) {
                        return Result.failure(Exception("${moveType.name} movement ingredient/area mismatch with stock count line"))
                    }
                }
                InventoryMovementType.MANUAL_ADJUSTMENT -> {
                    if (docType != SourceDocumentType.MANUAL) {
                        return Result.failure(Exception("MANUAL_ADJUSTMENT movement must use MANUAL source document type"))
                    }
                }
                InventoryMovementType.REVERSAL -> {
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
                        return Result.failure(Exception("Multiple REVERSAL movements pointing to the same original movement"))
                    }
                    reversedMovementIds.add(targetId)

                    if (move.restaurantId != original.restaurantId ||
                        move.ingredientId != original.ingredientId ||
                        move.areaId != original.areaId) {
                        return Result.failure(Exception("REVERSAL movement restaurant/ingredient/area mismatch with original"))
                    }

                    val origQty = try { BigDecimal(original.quantityBaseSigned) } catch (e: Exception) {
                        return Result.failure(Exception("Invalid original movement quantity format"))
                    }
                    if (moveQty.add(origQty).compareTo(BigDecimal.ZERO) != 0) {
                        return Result.failure(Exception("REVERSAL movement quantity is not the exact negation of original"))
                    }

                    if (move.totalValueSnapshot != null && original.totalValueSnapshot != null) {
                        val revVal = try { BigDecimal(move.totalValueSnapshot) } catch (e: Exception) { return Result.failure(Exception("Invalid reversal value format")) }
                        val origVal = try { BigDecimal(original.totalValueSnapshot) } catch (e: Exception) { return Result.failure(Exception("Invalid original value format")) }
                        if (revVal.add(origVal).compareTo(BigDecimal.ZERO) != 0) {
                            return Result.failure(Exception("REVERSAL movement totalValueSnapshot is not the exact negation of original"))
                        }
                    }
                }
            }
        }

        // 7. Document-to-Movement Lifecycle Consistency Validation
        // Purchases:
        for (receipt in dto.purchaseReceipts) {
            val status = DocumentStatus.valueOf(receipt.status)
            val lines = dto.purchaseLines.filter { it.purchaseReceiptId == receipt.id }
            val movements = dto.inventoryMovements.filter { it.sourceDocumentType == SourceDocumentType.PURCHASE_RECEIPT.name && it.sourceDocumentId == receipt.id }
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (movements.isNotEmpty()) {
                        return Result.failure(Exception("DRAFT purchase receipt must not have movements"))
                    }
                }
                DocumentStatus.POSTED -> {
                    val purchaseMoves = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
                    if (purchaseMoves.size != lines.size) {
                        return Result.failure(Exception("POSTED purchase receipt must have exactly one PURCHASE movement per line"))
                    }
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
                        return Result.failure(Exception("POSTED purchase receipt must not have REVERSAL movements"))
                    }
                }
                DocumentStatus.VOIDED -> {
                    val purchaseMoves = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (purchaseMoves.size != lines.size || reversalMoves.size != lines.size) {
                        return Result.failure(Exception("VOIDED purchase receipt must have matching PURCHASE and REVERSAL movements"))
                    }
                }
            }
        }

        // Waste:
        for (waste in dto.wasteEvents) {
            val status = DocumentStatus.valueOf(waste.status)
            val movements = dto.inventoryMovements.filter { it.sourceDocumentType == SourceDocumentType.WASTE_EVENT.name && it.sourceDocumentId == waste.id }
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return Result.failure(Exception("DRAFT waste event must not have movements"))
                }
                DocumentStatus.POSTED -> {
                    val wasteMoves = movements.filter { it.movementType == InventoryMovementType.WASTE.name }
                    if (wasteMoves.size != 1) return Result.failure(Exception("POSTED waste event must have exactly one WASTE movement"))
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
                        return Result.failure(Exception("POSTED waste event must not have REVERSAL movements"))
                    }
                }
                DocumentStatus.VOIDED -> {
                    val wasteMoves = movements.filter { it.movementType == InventoryMovementType.WASTE.name }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (wasteMoves.size != 1 || reversalMoves.size != 1) {
                        return Result.failure(Exception("VOIDED waste event must have exactly one WASTE movement and one REVERSAL"))
                    }
                }
            }
        }

        // Stock Counts:
        for (count in dto.stockCounts) {
            val status = StockCountStatus.valueOf(count.status)
            val scas = dto.stockCountAreas.filter { it.stockCountId == count.id }
            val countLines = dto.stockCountLines.filter { line -> scas.any { it.id == line.stockCountAreaId } }
            val movements = dto.inventoryMovements.filter { it.sourceDocumentType == SourceDocumentType.STOCK_COUNT.name && it.sourceDocumentId == count.id }
            when (status) {
                StockCountStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return Result.failure(Exception("DRAFT stock count must not have movements"))
                }
                StockCountStatus.COMPLETED -> {
                    val origMoves = movements.filter { it.movementType == InventoryMovementType.OPENING_BALANCE.name || it.movementType == InventoryMovementType.COUNT_ADJUSTMENT.name }
                    if (origMoves.size != countLines.size) {
                        return Result.failure(Exception("COMPLETED stock count must have one movement per line"))
                    }
                }
                StockCountStatus.VOIDED -> {
                    val origMoves = movements.filter { it.movementType == InventoryMovementType.OPENING_BALANCE.name || it.movementType == InventoryMovementType.COUNT_ADJUSTMENT.name }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (origMoves.size != reversalMoves.size) {
                        return Result.failure(Exception("VOIDED stock count must have a REVERSAL for each original movement"))
                    }
                }
            }
        }

        // 8. Balance Projection Consistency against Movements
        val computedBalances = mutableMapOf<Pair<String, String>, BigDecimal>()
        for (move in dto.inventoryMovements) {
            val key = Pair(move.ingredientId, move.areaId)
            val q = try { BigDecimal(move.quantityBaseSigned) } catch (e: Exception) { BigDecimal.ZERO }
            computedBalances[key] = computedBalances.getOrDefault(key, BigDecimal.ZERO).add(q)
        }

        for (proj in dto.inventoryBalanceProjections) {
            val key = Pair(proj.ingredientId, proj.areaId)
            val expected = computedBalances.getOrDefault(key, BigDecimal.ZERO)
            val actual = try { BigDecimal(proj.quantityBase) } catch (e: Exception) {
                return Result.failure(Exception("Invalid quantityBase in balance projection"))
            }
            if (expected.compareTo(actual) != 0) {
                return Result.failure(Exception("Inventory balance projection does not match movement history sum"))
            }
        }

        return Result.success(Unit)
    }
}
