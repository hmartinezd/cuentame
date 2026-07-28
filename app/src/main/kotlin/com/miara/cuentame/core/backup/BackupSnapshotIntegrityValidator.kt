package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import java.math.BigDecimal

object BackupSnapshotIntegrityValidator {

    private val VALID_PURCHASE_STATUSES = setOf("DRAFT", "POSTED", "VOIDED")
    private val VALID_STOCK_COUNT_STATUSES = setOf("DRAFT", "COMPLETED", "VOIDED")
    private val VALID_STOCK_COUNT_AREA_STATUSES = setOf("NOT_STARTED", "IN_PROGRESS", "COMPLETED")
    private val VALID_WASTE_STATUSES = setOf("DRAFT", "POSTED", "VOIDED")
    private val VALID_MOVEMENT_TYPES = setOf(
        "PURCHASE_POST", "PURCHASE_VOID",
        "STOCK_COUNT_ADJUSTMENT", "STOCK_COUNT_VOID",
        "WASTE_POST", "WASTE_VOID"
    )

    /**
     * Validates logical consistency and restaurant isolation of a backup snapshot DTO.
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

        // Maps for fast FK lookup and parent entity resolution
        val unitById = dto.units.associateBy { it.id }
        val areaById = dto.inventoryAreas.associateBy { it.id }
        val catById = dto.ingredientCategories.associateBy { it.id }
        val ingById = dto.ingredients.associateBy { it.id }
        val optionById = dto.ingredientUnitOptions.associateBy { it.id }
        val supplierById = dto.suppliers.associateBy { it.id }
        val receiptById = dto.purchaseReceipts.associateBy { it.id }
        val countById = dto.stockCounts.associateBy { it.id }
        val countAreaById = dto.stockCountAreas.associateBy { it.id }
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
            // Check that unit option belongs to the SAME ingredient as purchase line
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
            // Check that unit option belongs to the SAME ingredient as stock count line
            if (option.ingredientId != scl.ingredientId) {
                return Result.failure(Exception("Unit option ingredient mismatch in stock_count_lines"))
            }
        }

        for (waste in dto.wasteEvents) {
            if (!ingById.containsKey(waste.ingredientId)) return Result.failure(Exception("Broken FK: waste_event to ingredient"))
            if (!areaById.containsKey(waste.areaId)) return Result.failure(Exception("Broken FK: waste_event to area"))
            val option = optionById[waste.ingredientUnitOptionId] ?: return Result.failure(Exception("Broken FK: waste_event to unit_option"))
            // Check that unit option belongs to the SAME ingredient as waste event
            if (option.ingredientId != waste.ingredientId) {
                return Result.failure(Exception("Unit option ingredient mismatch in waste_events"))
            }
        }

        for (movement in dto.inventoryMovements) {
            if (!ingById.containsKey(movement.ingredientId)) return Result.failure(Exception("Broken FK: movement to ingredient"))
            if (!areaById.containsKey(movement.areaId)) return Result.failure(Exception("Broken FK: movement to area"))
            if (movement.reversalOfMovementId != null) {
                if (movement.reversalOfMovementId == movement.id) {
                    return Result.failure(Exception("Self-reversal in inventory_movements"))
                }
                if (!movementById.containsKey(movement.reversalOfMovementId)) {
                    return Result.failure(Exception("Broken FK: movement to reversal movement"))
                }
            }
        }

        for (bal in dto.inventoryBalanceProjections) {
            if (!ingById.containsKey(bal.ingredientId)) return Result.failure(Exception("Broken FK: balance_projection to ingredient"))
            if (!areaById.containsKey(bal.areaId)) return Result.failure(Exception("Broken FK: balance_projection to area"))
        }

        for (cost in dto.ingredientCostProjections) {
            if (!ingById.containsKey(cost.ingredientId)) return Result.failure(Exception("Broken FK: cost_projection to ingredient"))
        }

        // 5. Enum & Status String Validation
        if (dto.purchaseReceipts.any { it.status !in VALID_PURCHASE_STATUSES }) return Result.failure(Exception("Invalid purchase receipt status"))
        if (dto.stockCounts.any { it.status !in VALID_STOCK_COUNT_STATUSES }) return Result.failure(Exception("Invalid stock count status"))
        if (dto.stockCountAreas.any { it.status !in VALID_STOCK_COUNT_AREA_STATUSES }) return Result.failure(Exception("Invalid stock count area status"))
        if (dto.wasteEvents.any { it.status !in VALID_WASTE_STATUSES }) return Result.failure(Exception("Invalid waste event status"))
        if (dto.inventoryMovements.any { it.movementType !in VALID_MOVEMENT_TYPES }) return Result.failure(Exception("Invalid inventory movement type"))

        // 6. Decimal Fields Validation (Redacted error messages)
        fun checkDecimal(valStr: String?, tableName: String, fieldName: String) {
            if (valStr == null) return
            try {
                BigDecimal(valStr)
            } catch (e: Exception) {
                throw Exception("Invalid decimal format in $tableName.$fieldName")
            }
        }

        try {
            dto.units.forEach { checkDecimal(it.factorToCanonical, "units", "factorToCanonical") }
            dto.ingredients.forEach { checkDecimal(it.reorderPointBase, "ingredients", "reorderPointBase") }
            dto.ingredientUnitOptions.forEach { checkDecimal(it.factorToBase, "ingredient_unit_options", "factorToBase") }
            dto.purchaseLines.forEach { line ->
                checkDecimal(line.quantityEntered, "purchase_lines", "quantityEntered")
                checkDecimal(line.quantityBase, "purchase_lines", "quantityBase")
                checkDecimal(line.unitCostBase, "purchase_lines", "unitCostBase")
                checkDecimal(line.lineTotal, "purchase_lines", "lineTotal")
            }
            dto.stockCountLines.forEach { line ->
                checkDecimal(line.quantityEntered, "stock_count_lines", "quantityEntered")
                checkDecimal(line.quantityBase, "stock_count_lines", "quantityBase")
                checkDecimal(line.expectedQuantityBaseSnapshot, "stock_count_lines", "expectedQuantityBaseSnapshot")
                checkDecimal(line.adjustmentQuantityBase, "stock_count_lines", "adjustmentQuantityBase")
            }
            dto.wasteEvents.forEach { waste ->
                checkDecimal(waste.quantityEntered, "waste_events", "quantityEntered")
                checkDecimal(waste.quantityBase, "waste_events", "quantityBase")
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
