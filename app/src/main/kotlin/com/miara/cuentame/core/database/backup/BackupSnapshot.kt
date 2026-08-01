package com.miara.cuentame.core.database.backup

import com.miara.cuentame.core.database.entity.*

data class BackupSnapshot(
    val restaurants: List<RestaurantEntity>,
    val inventoryAreas: List<InventoryAreaEntity>,
    val ingredientCategories: List<IngredientCategoryEntity>,
    val units: List<UnitEntity>,
    val ingredients: List<IngredientEntity>,
    val ingredientUnitOptions: List<IngredientUnitOptionEntity>,
    val suppliers: List<SupplierEntity>,
    val purchaseReceipts: List<PurchaseReceiptEntity>,
    val purchaseLines: List<PurchaseLineEntity>,
    val stockCounts: List<StockCountEntity>,
    val stockCountAreas: List<StockCountAreaEntity>,
    val stockCountLines: List<StockCountLineEntity>,
    val wasteEvents: List<WasteEventEntity>,
    val inventoryMovements: List<InventoryMovementEntity>,
    val inventoryBalanceProjections: List<InventoryBalanceProjectionEntity>,
    val ingredientCostProjections: List<IngredientCostProjectionEntity>,
    val preparationRecipes: List<PreparationRecipeEntity>,
    val preparationRecipeComponents: List<PreparationRecipeComponentEntity>
)
