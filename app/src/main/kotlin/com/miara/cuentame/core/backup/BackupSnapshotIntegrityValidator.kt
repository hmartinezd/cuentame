package com.miara.cuentame.core.backup

import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupSnapshot

object BackupSnapshotIntegrityValidator {

    /**
     * Validates logical consistency and restaurant isolation of a database snapshot.
     */
    fun validate(snapshot: BackupSnapshot, manifest: BackupManifest): Result<Unit> {
        val restaurantId = manifest.restaurantId ?: return Result.failure(Exception("Manifest is missing restaurantId"))
        
        // 1. Exactly one restaurant, matching manifest
        if (snapshot.restaurants.size != 1) {
            return Result.failure(Exception("Snapshot must contain exactly one restaurant. Found ${snapshot.restaurants.size}"))
        }
        val restaurant = snapshot.restaurants[0]
        if (restaurant.id != restaurantId) {
            return Result.failure(Exception("Snapshot restaurant ID ${restaurant.id} does not match manifest $restaurantId"))
        }
        if (restaurant.name != manifest.restaurantName) {
            return Result.failure(Exception("Snapshot restaurant name mismatch"))
        }

        // 2. Multi-tenant Isolation: All records must belong to this restaurant
        if (snapshot.inventoryAreas.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table inventory_areas contains data for another restaurant"))
        if (snapshot.ingredientCategories.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table ingredient_categories contains data for another restaurant"))
        if (snapshot.ingredients.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table ingredients contains data for another restaurant"))
        if (snapshot.suppliers.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table suppliers contains data for another restaurant"))
        if (snapshot.purchaseReceipts.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table purchase_receipts contains data for another restaurant"))
        if (snapshot.stockCounts.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table stock_counts contains data for another restaurant"))
        if (snapshot.wasteEvents.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table waste_events contains data for another restaurant"))
        if (snapshot.inventoryMovements.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table inventory_movements contains data for another restaurant"))
        if (snapshot.inventoryBalanceProjections.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table inventory_balance_projection contains data for another restaurant"))
        if (snapshot.ingredientCostProjections.any { it.restaurantId != restaurantId }) return Result.failure(Exception("Isolation error: Table ingredient_cost_projection contains data for another restaurant"))

        // 3. Primary Key Uniqueness
        fun <T> checkUniqueness(list: List<T>, selector: (T) -> String, tableName: String): Result<Unit>? {
            val ids = list.map(selector)
            if (ids.distinct().size != ids.size) return Result.failure(Exception("Data integrity error: Duplicate primary keys in table $tableName"))
            return null
        }
        
        checkUniqueness(snapshot.restaurants, { it.id }, "restaurants")?.let { return it }
        checkUniqueness(snapshot.inventoryAreas, { it.id }, "inventory_areas")?.let { return it }
        checkUniqueness(snapshot.ingredients, { it.id }, "ingredients")?.let { return it }
        checkUniqueness(snapshot.suppliers, { it.id }, "suppliers")?.let { return it }
        checkUniqueness(snapshot.purchaseReceipts, { it.id }, "purchase_receipts")?.let { return it }
        checkUniqueness(snapshot.stockCounts, { it.id }, "stock_counts")?.let { return it }
        checkUniqueness(snapshot.wasteEvents, { it.id }, "waste_events")?.let { return it }
        checkUniqueness(snapshot.inventoryMovements, { it.id }, "inventory_movements")?.let { return it }

        // 4. Relational Integrity (Foreign Keys)
        val unitIds = snapshot.units.map { it.id }.toSet()
        val areaIds = snapshot.inventoryAreas.map { it.id }.toSet()
        val catIds = snapshot.ingredientCategories.map { it.id }.toSet()
        val ingIds = snapshot.ingredients.map { it.id }.toSet()
        val optIds = snapshot.ingredientUnitOptions.map { it.id }.toSet()
        val supIds = snapshot.suppliers.map { it.id }.toSet()
        val receiptIds = snapshot.purchaseReceipts.map { it.id }.toSet()
        val countIds = snapshot.stockCounts.map { it.id }.toSet()
        val scAreaIds = snapshot.stockCountAreas.map { it.id }.toSet()
        val moveIds = snapshot.inventoryMovements.map { it.id }.toSet()

        if (snapshot.ingredients.any { it.baseUnitId !in unitIds }) return Result.failure(Exception("Broken relationship: Ingredient references unknown unit"))
        if (snapshot.ingredients.any { it.categoryId != null && it.categoryId !in catIds }) return Result.failure(Exception("Broken relationship: Ingredient references unknown category"))
        if (snapshot.ingredients.any { it.defaultAreaId != null && it.defaultAreaId !in areaIds }) return Result.failure(Exception("Broken relationship: Ingredient references unknown area"))
        
        if (snapshot.ingredientUnitOptions.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken relationship: Unit option references unknown ingredient"))
        
        if (snapshot.purchaseReceipts.any { it.supplierId != null && it.supplierId !in supIds }) return Result.failure(Exception("Broken relationship: Purchase references unknown supplier"))
        
        if (snapshot.purchaseLines.any { it.purchaseReceiptId !in receiptIds }) return Result.failure(Exception("Broken relationship: Purchase line references unknown receipt"))
        if (snapshot.purchaseLines.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken relationship: Purchase line references unknown ingredient"))
        if (snapshot.purchaseLines.any { it.areaId !in areaIds }) return Result.failure(Exception("Broken relationship: Purchase line references unknown area"))
        if (snapshot.purchaseLines.any { it.ingredientUnitOptionId !in optIds }) return Result.failure(Exception("Broken relationship: Purchase line references unknown unit option"))

        if (snapshot.stockCountAreas.any { it.stockCountId !in countIds }) return Result.failure(Exception("Broken relationship: StockCountArea references unknown StockCount"))
        if (snapshot.stockCountAreas.any { it.areaId !in areaIds }) return Result.failure(Exception("Broken relationship: StockCountArea references unknown Area"))
        
        if (snapshot.stockCountLines.any { it.stockCountAreaId !in scAreaIds }) return Result.failure(Exception("Broken relationship: StockCountLine references unknown StockCountArea"))
        if (snapshot.stockCountLines.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken relationship: StockCountLine references unknown Ingredient"))
        
        if (snapshot.wasteEvents.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken relationship: WasteEvent references unknown Ingredient"))
        if (snapshot.wasteEvents.any { it.areaId !in areaIds }) return Result.failure(Exception("Broken relationship: WasteEvent references unknown Area"))
        if (snapshot.wasteEvents.any { it.ingredientUnitOptionId !in optIds }) return Result.failure(Exception("Broken relationship: WasteEvent references unknown Unit Option"))

        if (snapshot.inventoryMovements.any { it.ingredientId !in ingIds }) return Result.failure(Exception("Broken relationship: Movement references unknown Ingredient"))
        if (snapshot.inventoryMovements.any { it.areaId !in areaIds }) return Result.failure(Exception("Broken relationship: Movement references unknown Area"))
        if (snapshot.inventoryMovements.any { it.reversalOfMovementId != null && it.reversalOfMovementId !in moveIds }) return Result.failure(Exception("Broken relationship: Movement reversal references unknown movement"))
        if (snapshot.inventoryMovements.any { it.reversalOfMovementId == it.id }) return Result.failure(Exception("Broken relationship: Movement reverses itself"))

        return Result.success(Unit)
    }
}
