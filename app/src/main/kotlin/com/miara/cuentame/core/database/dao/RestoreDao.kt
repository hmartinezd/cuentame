package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.miara.cuentame.core.database.entity.*

@Dao
interface RestoreDao {

    @Query("DELETE FROM ingredient_cost_projection")
    suspend fun deleteAllIngredientCostProjections()

    @Query("DELETE FROM inventory_balance_projection")
    suspend fun deleteAllInventoryBalanceProjections()

    @Query("DELETE FROM inventory_movements")
    suspend fun deleteAllInventoryMovements()

    @Query("DELETE FROM stock_count_lines")
    suspend fun deleteAllStockCountLines()

    @Query("DELETE FROM stock_count_areas")
    suspend fun deleteAllStockCountAreas()

    @Query("DELETE FROM stock_counts")
    suspend fun deleteAllStockCounts()

    @Query("DELETE FROM purchase_lines")
    suspend fun deleteAllPurchaseLines()

    @Query("DELETE FROM purchase_receipts")
    suspend fun deleteAllPurchaseReceipts()

    @Query("DELETE FROM waste_events")
    suspend fun deleteAllWasteEvents()

    @Query("DELETE FROM production_batch_components")
    suspend fun deleteAllProductionBatchComponents()

    @Query("DELETE FROM production_batches")
    suspend fun deleteAllProductionBatches()

    @Query("DELETE FROM preparation_recipe_components")
    suspend fun deleteAllPreparationRecipeComponents()

    @Query("DELETE FROM preparation_recipes")
    suspend fun deleteAllPreparationRecipes()

    @Query("DELETE FROM ingredient_unit_options")
    suspend fun deleteAllIngredientUnitOptions()

    @Query("DELETE FROM ingredients")
    suspend fun deleteAllIngredients()

    @Query("DELETE FROM suppliers")
    suspend fun deleteAllSuppliers()

    @Query("DELETE FROM units")
    suspend fun deleteAllUnits()

    @Query("DELETE FROM ingredient_categories")
    suspend fun deleteAllIngredientCategories()

    @Query("DELETE FROM inventory_areas")
    suspend fun deleteAllInventoryAreas()

    @Query("DELETE FROM restaurants")
    suspend fun deleteAllRestaurants()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRestaurants(entities: List<RestaurantEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryAreas(entities: List<InventoryAreaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredientCategories(entities: List<IngredientCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUnits(entities: List<UnitEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSuppliers(entities: List<SupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredients(entities: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredientUnitOptions(entities: List<IngredientUnitOptionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseReceipts(entities: List<PurchaseReceiptEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseLines(entities: List<PurchaseLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockCounts(entities: List<StockCountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockCountAreas(entities: List<StockCountAreaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockCountLines(entities: List<StockCountLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWasteEvents(entities: List<WasteEventEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryMovements(entities: List<InventoryMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryBalanceProjections(entities: List<InventoryBalanceProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredientCostProjections(entities: List<IngredientCostProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreparationRecipes(entities: List<PreparationRecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreparationRecipeComponents(entities: List<PreparationRecipeComponentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductionBatches(entities: List<ProductionBatchEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductionBatchComponents(entities: List<ProductionBatchComponentEntity>)

    @Transaction
    suspend fun clearAllInOrder() {
        deleteAllIngredientCostProjections()
        deleteAllInventoryBalanceProjections()
        deleteAllInventoryMovements()
        deleteAllStockCountLines()
        deleteAllStockCountAreas()
        deleteAllStockCounts()
        deleteAllPurchaseLines()
        deleteAllPurchaseReceipts()
        deleteAllWasteEvents()
        deleteAllProductionBatchComponents()
        deleteAllProductionBatches()
        deleteAllPreparationRecipeComponents()
        deleteAllPreparationRecipes()
        deleteAllIngredientUnitOptions()
        deleteAllIngredients()
        deleteAllSuppliers()
        deleteAllUnits()
        deleteAllIngredientCategories()
        deleteAllInventoryAreas()
        deleteAllRestaurants()
    }
}
