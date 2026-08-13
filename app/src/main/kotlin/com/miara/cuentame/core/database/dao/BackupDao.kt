package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.database.backup.BackupSnapshot

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
            stockCountItemOrder = getStockCountItemOrder(restaurantId),
            wasteEvents = getWasteEvents(restaurantId),
            inventoryMovements = getInventoryMovements(restaurantId),
            inventoryBalanceProjections = getInventoryBalanceProjections(restaurantId),
            ingredientCostProjections = getIngredientCostProjections(restaurantId),
            preparationRecipes = getPreparationRecipes(restaurantId),
            preparationRecipeComponents = getPreparationRecipeComponents(restaurantId),
            productionBatches = getProductionBatches(restaurantId),
            productionBatchComponents = getProductionBatchComponents(restaurantId),
            purchaseInvoiceOcrResults = getPurchaseInvoiceOcrResults(restaurantId),
            purchaseInvoiceOcrPages = getPurchaseInvoiceOcrPages(restaurantId),
            purchaseInvoiceParseResults = getPurchaseInvoiceParseResults(restaurantId),
            purchaseInvoiceParsedLines = getPurchaseInvoiceParsedLines(restaurantId),
            supplierItemMappings = getSupplierItemMappings(restaurantId),
            purchaseInvoiceLineMatches = getPurchaseInvoiceLineMatches(restaurantId),
            purchaseInvoiceDraftApplications = getPurchaseInvoiceDraftApplications(restaurantId),
            purchaseInvoiceLineOrigins = getPurchaseInvoiceLineOrigins(restaurantId),
            menuRecipes = getMenuRecipes(restaurantId),
            menuRecipeComponents = getMenuRecipeComponents(restaurantId)
        )
    }

    @Query("SELECT * FROM restaurants WHERE id = :restaurantId")
    suspend fun getRestaurants(restaurantId: String): List<RestaurantEntity>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getInventoryAreas(restaurantId: String): List<InventoryAreaEntity>

    @Query("SELECT * FROM stock_count_item_order WHERE restaurantId = :restaurantId ORDER BY areaId, sortOrder, ingredientId")
    suspend fun getStockCountItemOrder(restaurantId: String): List<StockCountItemOrderEntity>

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

    @Query("SELECT * FROM preparation_recipes WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getPreparationRecipes(restaurantId: String): List<PreparationRecipeEntity>

    @Query("""
        SELECT prc.* FROM preparation_recipe_components prc
        JOIN preparation_recipes pr ON prc.recipeId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY prc.id ASC
    """)
    suspend fun getPreparationRecipeComponents(restaurantId: String): List<PreparationRecipeComponentEntity>

    @Query("SELECT * FROM production_batches WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getProductionBatches(restaurantId: String): List<ProductionBatchEntity>

    @Query("""
        SELECT pbc.* FROM production_batch_components pbc
        JOIN production_batches pb ON pbc.productionBatchId = pb.id
        WHERE pb.restaurantId = :restaurantId
        ORDER BY pbc.id ASC
    """)
    suspend fun getProductionBatchComponents(restaurantId: String): List<ProductionBatchComponentEntity>

    @Query("""
        SELECT ocr.* FROM purchase_invoice_ocr_results ocr
        JOIN purchase_receipts pr ON ocr.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY ocr.id ASC
    """)
    suspend fun getPurchaseInvoiceOcrResults(restaurantId: String): List<PurchaseInvoiceOcrResultEntity>

    @Query("""
        SELECT page.* FROM purchase_invoice_ocr_pages page
        JOIN purchase_invoice_ocr_results ocr ON page.ocrResultId = ocr.id
        JOIN purchase_receipts pr ON ocr.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY ocr.id ASC, page.pageIndex ASC
    """)
    suspend fun getPurchaseInvoiceOcrPages(restaurantId: String): List<PurchaseInvoiceOcrPageEntity>

    @Query("""
        SELECT parse.* FROM purchase_invoice_parse_results parse
        JOIN purchase_receipts pr ON parse.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY parse.id ASC
    """)
    suspend fun getPurchaseInvoiceParseResults(restaurantId: String): List<PurchaseInvoiceParseResultEntity>

    @Query("""
        SELECT line.* FROM purchase_invoice_parsed_lines line
        WHERE line.parseResultId IN (
            SELECT parse.id FROM purchase_invoice_parse_results parse
            JOIN purchase_receipts pr ON parse.purchaseReceiptId = pr.id
            WHERE pr.restaurantId = :restaurantId
        )
        ORDER BY line.parseResultId ASC, line.lineIndex ASC
    """)
    suspend fun getPurchaseInvoiceParsedLines(restaurantId: String): List<PurchaseInvoiceParsedLineEntity>

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getSupplierItemMappings(restaurantId: String): List<SupplierItemMappingEntity>

    @Query("""
        SELECT m.* FROM purchase_invoice_line_matches m
        WHERE m.parseResultId IN (
            SELECT parse.id FROM purchase_invoice_parse_results parse
            JOIN purchase_receipts pr ON parse.purchaseReceiptId = pr.id
            WHERE pr.restaurantId = :restaurantId
        )
        ORDER BY m.parseResultId ASC, m.lineIndex ASC
    """)
    suspend fun getPurchaseInvoiceLineMatches(restaurantId: String): List<PurchaseInvoiceLineMatchEntity>

    @Query("""
        SELECT app.* FROM purchase_invoice_draft_applications app
        JOIN purchase_receipts pr ON app.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY app.id ASC
    """)
    suspend fun getPurchaseInvoiceDraftApplications(restaurantId: String): List<PurchaseInvoiceDraftApplicationEntity>

    @Query("""
        SELECT origin.* FROM purchase_invoice_line_origins origin
        JOIN purchase_invoice_draft_applications app ON origin.applicationId = app.id
        JOIN purchase_receipts pr ON app.purchaseReceiptId = pr.id
        WHERE pr.restaurantId = :restaurantId
        ORDER BY origin.purchaseLineId ASC
    """)
    suspend fun getPurchaseInvoiceLineOrigins(restaurantId: String): List<PurchaseInvoiceLineOriginEntity>

    @Query("SELECT * FROM menu_recipes WHERE restaurantId = :restaurantId ORDER BY id ASC")
    suspend fun getMenuRecipes(restaurantId: String): List<MenuRecipeEntity>

    @Query("""
        SELECT mrc.* FROM menu_recipe_components mrc
        JOIN menu_recipes mr ON mrc.menuRecipeId = mr.id
        WHERE mr.restaurantId = :restaurantId
        ORDER BY mrc.id ASC
    """)
    suspend fun getMenuRecipeComponents(restaurantId: String): List<MenuRecipeComponentEntity>

    @Transaction
    suspend fun createGlobalSnapshot(): BackupSnapshot {
        return BackupSnapshot(
            restaurants = getAllRestaurants(),
            inventoryAreas = getAllInventoryAreas(),
            ingredientCategories = getAllIngredientCategories(),
            units = getUnits(),
            ingredients = getAllIngredients(),
            ingredientUnitOptions = getAllIngredientUnitOptions(),
            suppliers = getAllSuppliers(),
            purchaseReceipts = getAllPurchaseReceipts(),
            purchaseLines = getAllPurchaseLines(),
            stockCounts = getAllStockCounts(),
            stockCountAreas = getAllStockCountAreas(),
            stockCountLines = getAllStockCountLines(),
            stockCountItemOrder = getAllStockCountItemOrder(),
            wasteEvents = getAllWasteEvents(),
            inventoryMovements = getAllInventoryMovements(),
            inventoryBalanceProjections = getAllInventoryBalanceProjections(),
            ingredientCostProjections = getAllIngredientCostProjections(),
            preparationRecipes = getAllPreparationRecipes(),
            preparationRecipeComponents = getAllPreparationRecipeComponents(),
            productionBatches = getAllProductionBatches(),
            productionBatchComponents = getAllProductionBatchComponents(),
            purchaseInvoiceOcrResults = getAllPurchaseInvoiceOcrResults(),
            purchaseInvoiceOcrPages = getAllPurchaseInvoiceOcrPages(),
            purchaseInvoiceParseResults = getAllPurchaseInvoiceParseResults(),
            purchaseInvoiceParsedLines = getAllPurchaseInvoiceParsedLines(),
            supplierItemMappings = getAllSupplierItemMappings(),
            purchaseInvoiceLineMatches = getAllPurchaseInvoiceLineMatches(),
            purchaseInvoiceDraftApplications = getAllPurchaseInvoiceDraftApplications(),
            purchaseInvoiceLineOrigins = getAllPurchaseInvoiceLineOrigins(),
            menuRecipes = getAllMenuRecipes(),
            menuRecipeComponents = getAllMenuRecipeComponents()
        )
    }

    @Query("SELECT * FROM restaurants")
    suspend fun getAllRestaurants(): List<RestaurantEntity>

    @Query("SELECT * FROM inventory_areas")
    suspend fun getAllInventoryAreas(): List<InventoryAreaEntity>

    @Query("SELECT * FROM ingredient_categories")
    suspend fun getAllIngredientCategories(): List<IngredientCategoryEntity>

    @Query("SELECT * FROM ingredients")
    suspend fun getAllIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM ingredient_unit_options")
    suspend fun getAllIngredientUnitOptions(): List<IngredientUnitOptionEntity>

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliers(): List<SupplierEntity>

    @Query("SELECT * FROM purchase_receipts")
    suspend fun getAllPurchaseReceipts(): List<PurchaseReceiptEntity>

    @Query("SELECT * FROM purchase_lines")
    suspend fun getAllPurchaseLines(): List<PurchaseLineEntity>

    @Query("SELECT * FROM stock_counts")
    suspend fun getAllStockCounts(): List<StockCountEntity>

    @Query("SELECT * FROM stock_count_areas")
    suspend fun getAllStockCountAreas(): List<StockCountAreaEntity>

    @Query("SELECT * FROM stock_count_lines")
    suspend fun getAllStockCountLines(): List<StockCountLineEntity>

    @Query("SELECT * FROM stock_count_item_order ORDER BY areaId, sortOrder, ingredientId")
    suspend fun getAllStockCountItemOrder(): List<StockCountItemOrderEntity>

    @Query("SELECT * FROM waste_events")
    suspend fun getAllWasteEvents(): List<WasteEventEntity>

    @Query("SELECT * FROM inventory_movements")
    suspend fun getAllInventoryMovements(): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_balance_projection")
    suspend fun getAllInventoryBalanceProjections(): List<InventoryBalanceProjectionEntity>

    @Query("SELECT * FROM ingredient_cost_projection")
    suspend fun getAllIngredientCostProjections(): List<IngredientCostProjectionEntity>

    @Query("SELECT * FROM preparation_recipes")
    suspend fun getAllPreparationRecipes(): List<PreparationRecipeEntity>

    @Query("SELECT * FROM preparation_recipe_components")
    suspend fun getAllPreparationRecipeComponents(): List<PreparationRecipeComponentEntity>

    @Query("SELECT * FROM production_batches")
    suspend fun getAllProductionBatches(): List<ProductionBatchEntity>

    @Query("SELECT * FROM production_batch_components")
    suspend fun getAllProductionBatchComponents(): List<ProductionBatchComponentEntity>

    @Query("SELECT * FROM purchase_invoice_ocr_results")
    suspend fun getAllPurchaseInvoiceOcrResults(): List<PurchaseInvoiceOcrResultEntity>

    @Query("SELECT * FROM purchase_invoice_ocr_pages")
    suspend fun getAllPurchaseInvoiceOcrPages(): List<PurchaseInvoiceOcrPageEntity>

    @Query("SELECT * FROM purchase_invoice_parse_results")
    suspend fun getAllPurchaseInvoiceParseResults(): List<PurchaseInvoiceParseResultEntity>

    @Query("SELECT * FROM purchase_invoice_parsed_lines")
    suspend fun getAllPurchaseInvoiceParsedLines(): List<PurchaseInvoiceParsedLineEntity>

    @Query("SELECT * FROM supplier_item_mappings")
    suspend fun getAllSupplierItemMappings(): List<SupplierItemMappingEntity>

    @Query("SELECT * FROM purchase_invoice_line_matches")
    suspend fun getAllPurchaseInvoiceLineMatches(): List<PurchaseInvoiceLineMatchEntity>

    @Query("SELECT * FROM purchase_invoice_draft_applications")
    suspend fun getAllPurchaseInvoiceDraftApplications(): List<PurchaseInvoiceDraftApplicationEntity>

    @Query("SELECT * FROM purchase_invoice_line_origins")
    suspend fun getAllPurchaseInvoiceLineOrigins(): List<PurchaseInvoiceLineOriginEntity>

    @Query("SELECT * FROM menu_recipes")
    suspend fun getAllMenuRecipes(): List<MenuRecipeEntity>

    @Query("SELECT * FROM menu_recipe_components")
    suspend fun getAllMenuRecipeComponents(): List<MenuRecipeComponentEntity>
}
