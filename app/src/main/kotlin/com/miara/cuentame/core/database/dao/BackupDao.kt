package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.backup.BackupSnapshot

@Dao
interface BackupDao {

    @Transaction
    @Query("SELECT * FROM restaurants") // This query is just a trigger for the transaction wrapper if needed, but we will use the default implementation for other methods.
    suspend fun createSnapshot(): BackupSnapshot {
        // Room doesn't automatically map a multi-table result to BackupSnapshot unless we define it.
        // We will implement this in a class that has access to all individual DAOs or use queries here.
        return BackupSnapshot(
            restaurants = getRestaurants(),
            inventoryAreas = getInventoryAreas(),
            ingredientCategories = getIngredientCategories(),
            units = getUnits(),
            ingredients = getIngredients(),
            ingredientUnitOptions = getIngredientUnitOptions(),
            suppliers = getSuppliers(),
            purchaseReceipts = getPurchaseReceipts(),
            purchaseLines = getPurchaseLines(),
            stockCounts = getStockCounts(),
            stockCountAreas = getStockCountAreas(),
            stockCountLines = getStockCountLines(),
            wasteEvents = getWasteEvents(),
            inventoryMovements = getInventoryMovements(),
            inventoryBalanceProjections = getInventoryBalanceProjections(),
            ingredientCostProjections = getIngredientCostProjections()
        )
    }

    @Query("SELECT * FROM restaurants ORDER BY id ASC")
    suspend fun getRestaurants(): List<RestaurantEntity>

    @Query("SELECT * FROM inventory_areas ORDER BY id ASC")
    suspend fun getInventoryAreas(): List<InventoryAreaEntity>

    @Query("SELECT * FROM ingredient_categories ORDER BY id ASC")
    suspend fun getIngredientCategories(): List<IngredientCategoryEntity>

    @Query("SELECT * FROM units ORDER BY id ASC")
    suspend fun getUnits(): List<UnitEntity>

    @Query("SELECT * FROM ingredients ORDER BY id ASC")
    suspend fun getIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM ingredient_unit_options ORDER BY id ASC")
    suspend fun getIngredientUnitOptions(): List<IngredientUnitOptionEntity>

    @Query("SELECT * FROM suppliers ORDER BY id ASC")
    suspend fun getSuppliers(): List<SupplierEntity>

    @Query("SELECT * FROM purchase_receipts ORDER BY id ASC")
    suspend fun getPurchaseReceipts(): List<PurchaseReceiptEntity>

    @Query("SELECT * FROM purchase_lines ORDER BY id ASC")
    suspend fun getPurchaseLines(): List<PurchaseLineEntity>

    @Query("SELECT * FROM stock_counts ORDER BY id ASC")
    suspend fun getStockCounts(): List<StockCountEntity>

    @Query("SELECT * FROM stock_count_areas ORDER BY id ASC")
    suspend fun getStockCountAreas(): List<StockCountAreaEntity>

    @Query("SELECT * FROM stock_count_lines ORDER BY id ASC")
    suspend fun getStockCountLines(): List<StockCountLineEntity>

    @Query("SELECT * FROM waste_events ORDER BY id ASC")
    suspend fun getWasteEvents(): List<WasteEventEntity>

    @Query("SELECT * FROM inventory_movements ORDER BY id ASC")
    suspend fun getInventoryMovements(): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_balance_projection ORDER BY restaurantId ASC, ingredientId ASC, areaId ASC")
    suspend fun getInventoryBalanceProjections(): List<InventoryBalanceProjectionEntity>

    @Query("SELECT * FROM ingredient_cost_projection ORDER BY restaurantId ASC, ingredientId ASC")
    suspend fun getIngredientCostProjections(): List<IngredientCostProjectionEntity>
}
