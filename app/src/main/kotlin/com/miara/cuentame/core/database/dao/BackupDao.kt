package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.backup.BackupSnapshot

@Dao
interface BackupDao {

    @Transaction
    suspend fun createSnapshot(restaurantId: String): BackupSnapshot {
        return BackupSnapshot(
            restaurants = getRestaurants(restaurantId),
            inventoryAreas = getInventoryAreas(restaurantId),
            ingredientCategories = getIngredientCategories(restaurantId),
            units = getUnits(), // Units are global reference data
            ingredients = getIngredients(restaurantId),
            ingredientUnitOptions = getIngredientUnitOptions(restaurantId),
            suppliers = getSuppliers(restaurantId),
            purchaseReceipts = getPurchaseReceipts(restaurantId),
            purchaseLines = getPurchaseLines(restaurantId),
            stockCounts = getStockCounts(restaurantId),
            stockCountAreas = getStockCountAreas(restaurantId),
            stockCountLines = getStockCountLines(restaurantId),
            wasteEvents = getWasteEvents(restaurantId),
            inventoryMovements = getInventoryMovements(restaurantId),
            inventoryBalanceProjections = getInventoryBalanceProjections(restaurantId),
            ingredientCostProjections = getIngredientCostProjections(restaurantId)
        )
    }

    @Query("SELECT * FROM restaurants WHERE id = :restaurantId")
    suspend fun getRestaurants(restaurantId: String): List<RestaurantEntity>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getInventoryAreas(restaurantId: String): List<InventoryAreaEntity>

    @Query("SELECT * FROM ingredient_categories WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getIngredientCategories(restaurantId: String): List<IngredientCategoryEntity>

    @Query("SELECT * FROM units ORDER BY id ASC")
    suspend fun getUnits(): List<UnitEntity>

    @Query("SELECT * FROM ingredients WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getIngredients(restaurantId: String): List<IngredientEntity>

    @Query("""
        SELECT iuo.* FROM ingredient_unit_options iuo
        JOIN ingredients i ON iuo.ingredientId = i.id
        WHERE i.restaurantId = :restaurantId
        ORDER BY iuo.id ASC
    """)
    suspend fun getIngredientUnitOptions(restaurantId: String): List<IngredientUnitOptionEntity>

    @Query("SELECT * FROM suppliers WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getSuppliers(restaurantId: String): List<SupplierEntity>

    @Query("SELECT * FROM purchase_receipts WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getPurchaseReceipts(restaurantId: String): List<PurchaseReceiptEntity>

    @Query("""
        SELECT pl.* FROM purchase_lines pl
        JOIN purchase_receipts pr ON pl.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY pl.id ASC
    """)
    suspend fun getPurchaseLines(restaurantId: String): List<PurchaseLineEntity>

    @Query("SELECT * FROM stock_counts WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getStockCounts(restaurantId: String): List<StockCountEntity>

    @Query("""
        SELECT sca.* FROM stock_count_areas sca
        JOIN stock_counts sc ON sca.stockCountId = sc.id
        WHERE sc.restaurantId = :restaurantId
        ORDER BY sca.id ASC
    """)
    suspend fun getStockCountAreas(restaurantId: String): List<StockCountAreaEntity>

    @Query("""
        SELECT scl.* FROM stock_count_lines scl
        JOIN stock_count_areas sca ON scl.stockCountAreaId = sca.id
        JOIN stock_counts sc ON sca.stockCountId = sc.id
        WHERE sc.restaurantId = :restaurantId
        ORDER BY scl.id ASC
    """)
    suspend fun getStockCountLines(restaurantId: String): List<StockCountLineEntity>

    @Query("SELECT * FROM waste_events WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getWasteEvents(restaurantId: String): List<WasteEventEntity>

    @Query("SELECT * FROM inventory_movements WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getInventoryMovements(restaurantId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_balance_projection WHERE restaurantId = :restaurantId ORDER BY ingredientId ASC, areaId ASC")
    suspend fun getInventoryBalanceProjections(restaurantId: String): List<InventoryBalanceProjectionEntity>

    @Query("SELECT * FROM ingredient_cost_projection WHERE restaurantId = :restaurantId ORDER BY ingredientId ASC")
    suspend fun getIngredientCostProjections(restaurantId: String): List<IngredientCostProjectionEntity>
}
