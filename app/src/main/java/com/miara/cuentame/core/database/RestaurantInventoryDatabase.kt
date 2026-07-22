package com.miara.cuentame.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.dao.WasteDao
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.StockCountAreaEntity
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.database.entity.StockCountLineEntity
import com.miara.cuentame.core.database.entity.SupplierEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity

@Database(
    entities = [
        RestaurantEntity::class,
        InventoryAreaEntity::class,
        IngredientCategoryEntity::class,
        UnitEntity::class,
        IngredientEntity::class,
        IngredientUnitOptionEntity::class,
        SupplierEntity::class,
        PurchaseReceiptEntity::class,
        PurchaseLineEntity::class,
        StockCountEntity::class,
        StockCountAreaEntity::class,
        StockCountLineEntity::class,
        WasteEventEntity::class,
        InventoryMovementEntity::class,
        InventoryBalanceProjectionEntity::class,
        IngredientCostProjectionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(com.miara.cuentame.core.database.converter.RoomTypeConverters::class)
abstract class RestaurantInventoryDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao
    abstract fun inventoryAreaDao(): InventoryAreaDao
    abstract fun ingredientCategoryDao(): IngredientCategoryDao
    abstract fun unitDao(): UnitDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun ingredientUnitOptionDao(): IngredientUnitOptionDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockCountDao(): StockCountDao
    abstract fun wasteDao(): WasteDao
    abstract fun inventoryMovementDao(): InventoryMovementDao
    abstract fun inventoryProjectionDao(): InventoryProjectionDao
    abstract fun ingredientCostProjectionDao(): IngredientCostProjectionDao
}
