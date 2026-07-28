package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import java.math.BigDecimal

object BackupSnapshotIntegrityValidator {

    /**
     * Validates logical consistency and restaurant isolation of a backup snapshot DTO.
     */
    fun validate(dto: BackupSnapshotDto, manifest: BackupManifest): Result<Unit> {
        val restaurantId = manifest.restaurantId ?: return Result.failure(Exception("Manifest missing restaurantId"))
        
        // 1. Restaurant consistency
        if (dto.restaurants.size != 1) {
            return Result.failure(Exception("Snapshot must contain exactly one restaurant. Found ${dto.restaurants.size}"))
        }
        val restaurant = dto.restaurants[0]
        if (restaurant.id != restaurantId) return Result.failure(Exception("Snapshot restaurant ID mismatch"))
        if (restaurant.name != manifest.restaurantName) return Result.failure(Exception("Snapshot restaurant name mismatch"))
        if (restaurant.currencyCode != manifest.currencyCode) return Result.failure(Exception("Snapshot currencyCode mismatch"))
        if (restaurant.localeTag != manifest.localeTag) return Result.failure(Exception("Snapshot localeTag mismatch"))

        // 2. Primary Key Uniqueness
        fun <T> checkUnique(list: List<T>, selector: (T) -> String, name: String): Result<Unit>? {
            val ids = list.map(selector)
            if (ids.any { it.isBlank() }) return Result.failure(Exception("Blank ID in table $name"))
            if (ids.distinct().size != ids.size) return Result.failure(Exception("Duplicate primary keys in table $name"))
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
        checkUnique(dto.wasteEvents, { it.id }, "waste_events")?.let { return it }
        checkUnique(dto.inventoryMovements, { it.id }, "inventory_movements")?.let { return it }

        // Composite Keys for Projections
        val balanceKeys = dto.inventoryBalanceProjections.map { "${it.restaurantId}|${it.ingredientId}|${it.areaId}" }
        if (balanceKeys.distinct().size != balanceKeys.size) return Result.failure(Exception("Duplicate keys in inventory_balance_projections"))
        
        val costKeys = dto.ingredientCostProjections.map { "${it.restaurantId}|${it.ingredientId}" }
        if (costKeys.distinct().size != costKeys.size) return Result.failure(Exception("Duplicate keys in ingredient_cost_projections"))

        // 3. Restaurant Isolation
        if (dto.inventoryAreas.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: inventory_areas"))
        if (dto.ingredientCategories.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: ingredient_categories"))
        if (dto.ingredients.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: ingredients"))
        if (dto.suppliers.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: suppliers"))
        if (dto.purchaseReceipts.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: purchase_receipts"))
        if (dto.stockCounts.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: stock_counts"))
        if (dto.wasteEvents.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: waste_events"))
        if (dto.inventoryMovements.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: inventory_movements"))
        if (dto.inventoryBalanceProjections.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: inventory_balance_projections"))
        if (dto.ingredientCostProjections.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: ingredient_cost_projections"))

        // 4. Foreign Key Integrity
        val unitIds = dto.units.map { it.id }.toSet()
        val areaIds = dto.inventoryAreas.map { it.id }.toSet()
        val catIds = dto.ingredientCategories.map { it.id }.toSet()
        val ingIds = dto.ingredients.map { it.id }.toSet()
        val optIds = dto.ingredientUnitOptions.map { it.id }.toSet()
        val supIds = dto.suppliers.map { it.id }.toSet()
        val receiptIds = dto.purchaseReceipts.map { it.id }.toSet()
        val moveIds = dto.inventoryMovements.map { it.id }.toSet()

        if (dto.ingredients.any { it.baseUnitId !in unitIds }) return Result.failure(Exception("Broken FK: ingredient -> unit"))
        if (dto.ingredients.any { it.categoryId != null && it.categoryId !in catIds }) return Result.failure(Exception("Broken FK: ingredient -> category"))
        if (dto.ingredients.any { it.defaultAreaId != null && it.defaultAreaId !in areaIds }) return Result.failure(Exception("Broken FK: ingredient -> area"))
        
        if (dto.ingredientUnitOptions.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken FK: unit_option -> ingredient"))
        
        if (dto.purchaseReceipts.any { it.supplierId != null && it.supplierId !in supIds }) return Result.failure(Exception("Broken FK: purchase -> supplier"))
        
        if (dto.purchaseLines.any { it.purchaseReceiptId !in receiptIds }) return Result.failure(Exception("Broken FK: purchase_line -> receipt"))
        if (dto.purchaseLines.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken FK: purchase_line -> ingredient"))
        if (dto.purchaseLines.any { it.areaId !in areaIds }) return Result.failure(Exception("Broken FK: purchase_line -> area"))
        if (dto.purchaseLines.any { it.ingredientUnitOptionId !in optIds }) return Result.failure(Exception("Broken FK: purchase_line -> unit_option"))

        if (dto.inventoryMovements.any { it.reversalOfMovementId != null && it.reversalOfMovementId !in moveIds }) return Result.failure(Exception("Broken FK: movement -> reversal"))
        if (dto.inventoryMovements.any { it.reversalOfMovementId == it.id }) return Result.failure(Exception("Self-reversal in movements"))

        // 5. Numeric & Semantic Validation
        fun validateDecimal(value: String, table: String, field: String, id: String) {
            try { BigDecimal(value) } catch (e: Exception) { throw Exception("Invalid decimal in $table.$field for ID $id: ${e.message}") }
        }

        try {
            dto.purchaseLines.forEach { validateDecimal(it.quantityEntered, "purchase_lines", "quantityEntered", it.id) }
            dto.purchaseLines.forEach { validateDecimal(it.lineTotal, "purchase_lines", "lineTotal", it.id) }
            dto.inventoryMovements.forEach { validateDecimal(it.quantityBaseSigned, "inventory_movements", "quantityBaseSigned", it.id) }
            dto.wasteEvents.forEach { validateDecimal(it.quantityEntered, "waste_events", "quantityEntered", it.id) }
        } catch (e: Exception) { return Result.failure(e) }

        return Result.success(Unit)
    }
}
