package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.miara.cuentame.core.database.entity.*

@Dao
interface RestoreDao {

    @Query("DELETE FROM stock_count_item_order")
    suspend fun deleteAllStockCountItemOrder()

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

    @Query("DELETE FROM purchase_invoice_ocr_pages")
    suspend fun deleteAllPurchaseInvoiceOcrPages()

    @Query("DELETE FROM purchase_invoice_parsed_lines")
    suspend fun deleteAllPurchaseInvoiceParsedLines()

    @Query("DELETE FROM purchase_invoice_line_matches")
    suspend fun deleteAllPurchaseInvoiceLineMatches()

    @Query("DELETE FROM purchase_invoice_line_origins")
    suspend fun deleteAllPurchaseInvoiceLineOrigins()

    @Query("DELETE FROM purchase_invoice_draft_applications")
    suspend fun deleteAllPurchaseInvoiceDraftApplications()

    @Query("DELETE FROM purchase_invoice_parse_results")
    suspend fun deleteAllPurchaseInvoiceParseResults()

    @Query("DELETE FROM purchase_invoice_ocr_results")
    suspend fun deleteAllPurchaseInvoiceOcrResults()

    @Query("DELETE FROM purchase_receipts")
    suspend fun deleteAllPurchaseReceipts()

    @Query("DELETE FROM waste_events")
    suspend fun deleteAllWasteEvents()

    @Query("DELETE FROM menu_recipe_components")
    suspend fun deleteAllMenuRecipeComponents()

    @Query("DELETE FROM menu_recipes")
    suspend fun deleteAllMenuRecipes()

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

    @Query("DELETE FROM supplier_item_mappings")
    suspend fun deleteAllSupplierItemMappings()

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
    suspend fun insertStockCountItemOrder(entities: List<StockCountItemOrderEntity>)

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceOcrResults(entities: List<PurchaseInvoiceOcrResultEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceOcrPages(entities: List<PurchaseInvoiceOcrPageEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceParseResults(entities: List<PurchaseInvoiceParseResultEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceParsedLines(entities: List<PurchaseInvoiceParsedLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceLineMatches(entities: List<PurchaseInvoiceLineMatchEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierItemMappings(entities: List<SupplierItemMappingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceDraftApplications(entities: List<PurchaseInvoiceDraftApplicationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceLineOrigins(entities: List<PurchaseInvoiceLineOriginEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMenuRecipes(entities: List<MenuRecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMenuRecipeComponents(entities: List<MenuRecipeComponentEntity>)

    @Transaction
    suspend fun clearAllInOrder() {
        deleteAllStockCountItemOrder()
        deleteAllIngredientCostProjections()
        deleteAllInventoryBalanceProjections()
        deleteAllInventoryMovements()
        deleteAllStockCountLines()
        deleteAllStockCountAreas()
        deleteAllStockCounts()
        deleteAllPurchaseLines()
        deleteAllPurchaseInvoiceOcrPages()
        deleteAllPurchaseInvoiceParsedLines()
        deleteAllPurchaseInvoiceLineMatches()
        deleteAllPurchaseInvoiceLineOrigins()
        deleteAllPurchaseInvoiceDraftApplications()
        deleteAllPurchaseInvoiceParseResults()
        deleteAllPurchaseInvoiceOcrResults()
        deleteAllPurchaseReceipts()
        deleteAllWasteEvents()
        deleteAllMenuRecipeComponents()
        deleteAllMenuRecipes()
        deleteAllProductionBatchComponents()
        deleteAllProductionBatches()
        deleteAllPreparationRecipeComponents()
        deleteAllPreparationRecipes()
        deleteAllSupplierItemMappings()
        deleteAllIngredientUnitOptions()
        deleteAllIngredients()
        deleteAllSuppliers()
        deleteAllUnits()
        deleteAllIngredientCategories()
        deleteAllInventoryAreas()
        deleteAllRestaurants()
    }
}
