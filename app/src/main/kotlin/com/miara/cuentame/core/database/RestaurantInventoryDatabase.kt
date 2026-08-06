package com.miara.cuentame.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.database.DatabaseSchema

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
        IngredientCostProjectionEntity::class,
        PreparationRecipeEntity::class,
        PreparationRecipeComponentEntity::class,
        ProductionBatchEntity::class,
        ProductionBatchComponentEntity::class
    ],
    version = DatabaseSchema.VERSION,
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
    abstract fun preparationRecipeDao(): PreparationRecipeDao
    abstract fun productionBatchDao(): ProductionBatchDao
    abstract fun backupDao(): BackupDao
    abstract fun restoreDao(): RestoreDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create temporary table with nullable averageUnitCostBase
                db.execSQL("CREATE TABLE IF NOT EXISTS `ingredient_cost_projection_new` (`restaurantId` TEXT NOT NULL, `ingredientId` TEXT NOT NULL, `averageUnitCostBase` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`restaurantId`, `ingredientId`))")
                
                // 2. Copy all existing rows
                db.execSQL("INSERT INTO `ingredient_cost_projection_new` (`restaurantId`, `ingredientId`, `averageUnitCostBase`, `updatedAt`) SELECT `restaurantId`, `ingredientId`, `averageUnitCostBase`, `updatedAt` FROM `ingredient_cost_projection`")
                
                // 3. Drop the old table
                db.execSQL("DROP TABLE `ingredient_cost_projection`")
                
                // 4. Rename the temporary table
                db.execSQL("ALTER TABLE `ingredient_cost_projection_new` RENAME TO `ingredient_cost_projection`")
                
                // 5. Recreate indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_cost_projection_restaurantId` ON `ingredient_cost_projection` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_cost_projection_ingredientId` ON `ingredient_cost_projection` (`ingredientId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create preparation_recipes table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `preparation_recipes` (
                        `id` TEXT NOT NULL, 
                        `restaurantId` TEXT NOT NULL, 
                        `outputIngredientId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `normalizedName` TEXT NOT NULL, 
                        `standardYieldQuantity` TEXT, 
                        `standardYieldQuantityBase` TEXT, 
                        `yieldUnitOptionId` TEXT, 
                        `status` TEXT NOT NULL, 
                        `notes` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `archivedAt` INTEGER, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`restaurantId`) REFERENCES `restaurants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`outputIngredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`yieldUnitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                
                // Create indices for preparation_recipes
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId` ON `preparation_recipes` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_outputIngredientId` ON `preparation_recipes` (`outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_yieldUnitOptionId` ON `preparation_recipes` (`yieldUnitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId_outputIngredientId` ON `preparation_recipes` (`restaurantId`, `outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId_status` ON `preparation_recipes` (`restaurantId`, `status`)")

                // Create preparation_recipe_components table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `preparation_recipe_components` (
                        `id` TEXT NOT NULL, 
                        `recipeId` TEXT NOT NULL, 
                        `componentIngredientId` TEXT NOT NULL, 
                        `unitOptionId` TEXT NOT NULL, 
                        `quantityEntered` TEXT NOT NULL, 
                        `quantityBase` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `notes` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`recipeId`) REFERENCES `preparation_recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`componentIngredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`unitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                
                // Create indices for preparation_recipe_components
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_recipeId` ON `preparation_recipe_components` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_componentIngredientId` ON `preparation_recipe_components` (`componentIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_unitOptionId` ON `preparation_recipe_components` (`unitOptionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_preparation_recipe_components_recipeId_componentIngredientId` ON `preparation_recipe_components` (`recipeId`, `componentIngredientId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create production_batches table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `production_batches` (
                        `id` TEXT NOT NULL, 
                        `restaurantId` TEXT NOT NULL, 
                        `recipeId` TEXT NOT NULL, 
                        `recipeNameSnapshot` TEXT NOT NULL, 
                        `outputIngredientId` TEXT NOT NULL, 
                        `batchMultiplier` TEXT NOT NULL, 
                        `recipeStandardYieldQuantitySnapshot` TEXT NOT NULL, 
                        `recipeStandardYieldBaseSnapshot` TEXT NOT NULL, 
                        `recipeYieldUnitOptionIdSnapshot` TEXT NOT NULL, 
                        `expectedOutputQuantityEntered` TEXT NOT NULL, 
                        `expectedOutputQuantityBase` TEXT NOT NULL, 
                        `actualOutputQuantityEntered` TEXT NOT NULL, 
                        `actualOutputQuantityBase` TEXT NOT NULL, 
                        `outputUnitOptionId` TEXT NOT NULL, 
                        `outputAreaId` TEXT NOT NULL, 
                        `hasManualOutputQuantityOverride` INTEGER NOT NULL, 
                        `totalComponentCostSnapshot` TEXT, 
                        `outputUnitCostBaseSnapshot` TEXT, 
                        `effectiveAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `notes` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `postedAt` INTEGER, 
                        `voidedAt` INTEGER, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`restaurantId`) REFERENCES `restaurants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`recipeId`) REFERENCES `preparation_recipes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`outputIngredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`outputAreaId`) REFERENCES `inventory_areas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`outputUnitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                // Create indices for production_batches
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId` ON `production_batches` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_recipeId` ON `production_batches` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputIngredientId` ON `production_batches` (`outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputAreaId` ON `production_batches` (`outputAreaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputUnitOptionId` ON `production_batches` (`outputUnitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_status` ON `production_batches` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_effectiveAt` ON `production_batches` (`effectiveAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId_effectiveAt` ON `production_batches` (`restaurantId`, `effectiveAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId_status` ON `production_batches` (`restaurantId`, `status`)")

                // Create production_batch_components table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `production_batch_components` (
                        `id` TEXT NOT NULL, 
                        `productionBatchId` TEXT NOT NULL, 
                        `sourceRecipeComponentIdSnapshot` TEXT NOT NULL, 
                        `componentIngredientId` TEXT NOT NULL, 
                        `recipeQuantityEnteredSnapshot` TEXT NOT NULL, 
                        `recipeQuantityBaseSnapshot` TEXT NOT NULL, 
                        `recipeUnitOptionIdSnapshot` TEXT NOT NULL, 
                        `expectedQuantityEntered` TEXT NOT NULL, 
                        `expectedQuantityBase` TEXT NOT NULL, 
                        `actualQuantityEntered` TEXT NOT NULL, 
                        `actualQuantityBase` TEXT NOT NULL, 
                        `unitOptionId` TEXT NOT NULL, 
                        `hasManualQuantityOverride` INTEGER NOT NULL, 
                        `sourceAreaId` TEXT, 
                        `unitCostBaseSnapshot` TEXT, 
                        `totalCostSnapshot` TEXT, 
                        `sortOrder` INTEGER NOT NULL, 
                        `notes` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`productionBatchId`) REFERENCES `production_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`componentIngredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`sourceAreaId`) REFERENCES `inventory_areas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`unitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                // Create indices for production_batch_components
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batch_components_productionBatchId` ON `production_batch_components` (`productionBatchId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batch_components_componentIngredientId` ON `production_batch_components` (`componentIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batch_components_sourceAreaId` ON `production_batch_components` (`sourceAreaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batch_components_unitOptionId` ON `production_batch_components` (`unitOptionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_production_batch_components_productionBatchId_componentIngredientId` ON `production_batch_components` (`productionBatchId`, `componentIngredientId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `purchase_receipts` ADD COLUMN `attachmentDisplayName` TEXT")
                db.execSQL("ALTER TABLE `waste_events` ADD COLUMN `attachmentDisplayName` TEXT")
            }
        }
    }
}
