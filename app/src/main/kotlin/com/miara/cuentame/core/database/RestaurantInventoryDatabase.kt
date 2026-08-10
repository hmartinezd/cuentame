package com.miara.cuentame.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.common.database.DatabaseSchema

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
        ProductionBatchComponentEntity::class,
        PurchaseInvoiceOcrResultEntity::class,
        PurchaseInvoiceOcrPageEntity::class,
        PurchaseInvoiceParseResultEntity::class,
        PurchaseInvoiceParsedLineEntity::class,
        SupplierItemMappingEntity::class,
        PurchaseInvoiceLineMatchEntity::class,
        PurchaseInvoiceDraftApplicationEntity::class,
        PurchaseInvoiceLineOriginEntity::class
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
    abstract fun purchaseOcrDao(): PurchaseOcrDao
    abstract fun purchaseParseDao(): PurchaseParseDao
    abstract fun supplierItemMappingDao(): SupplierItemMappingDao
    abstract fun purchaseInvoiceLineMatchDao(): PurchaseInvoiceLineMatchDao
    abstract fun purchaseInvoiceMaterializationDao(): PurchaseInvoiceMaterializationDao
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_ocr_results` (
                        `id` TEXT NOT NULL, 
                        `purchaseReceiptId` TEXT NOT NULL, 
                        `sourceDocumentSha256` TEXT NOT NULL, 
                        `sourceMimeType` TEXT NOT NULL, 
                        `engine` TEXT NOT NULL, 
                        `evidenceSchemaVersion` INTEGER NOT NULL, 
                        `pageCount` INTEGER NOT NULL, 
                        `fullText` TEXT NOT NULL, 
                        `processedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`purchaseReceiptId`) REFERENCES `purchase_receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_ocr_results_purchaseReceiptId` ON `purchase_invoice_ocr_results` (`purchaseReceiptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_ocr_results_sourceDocumentSha256` ON `purchase_invoice_ocr_results` (`sourceDocumentSha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_ocr_results_processedAt` ON `purchase_invoice_ocr_results` (`processedAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_ocr_pages` (
                        `ocrResultId` TEXT NOT NULL, 
                        `pageIndex` INTEGER NOT NULL, 
                        `widthPx` INTEGER NOT NULL, 
                        `heightPx` INTEGER NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `evidenceJson` TEXT NOT NULL, 
                        PRIMARY KEY(`ocrResultId`, `pageIndex`), 
                        FOREIGN KEY(`ocrResultId`) REFERENCES `purchase_invoice_ocr_results`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_ocr_pages_ocrResultId` ON `purchase_invoice_ocr_pages` (`ocrResultId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_parse_results` (
                        `id` TEXT NOT NULL, 
                        `purchaseReceiptId` TEXT NOT NULL, 
                        `ocrResultId` TEXT NOT NULL, 
                        `sourceDocumentSha256` TEXT NOT NULL, 
                        `parserEngine` TEXT NOT NULL, 
                        `parserSchemaVersion` INTEGER NOT NULL, 
                        `headerEvidenceJson` TEXT NOT NULL, 
                        `totalsEvidenceJson` TEXT NOT NULL, 
                        `correctionsJson` TEXT, 
                        `warningsJson` TEXT NOT NULL, 
                        `processedAt` INTEGER NOT NULL, 
                        `reviewedAt` INTEGER, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`purchaseReceiptId`) REFERENCES `purchase_receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`ocrResultId`) REFERENCES `purchase_invoice_ocr_results`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_parse_results_purchaseReceiptId` ON `purchase_invoice_parse_results` (`purchaseReceiptId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_parse_results_ocrResultId` ON `purchase_invoice_parse_results` (`ocrResultId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_parse_results_sourceDocumentSha256` ON `purchase_invoice_parse_results` (`sourceDocumentSha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_parse_results_processedAt` ON `purchase_invoice_parse_results` (`processedAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_parsed_lines` (
                        `parseResultId` TEXT NOT NULL, 
                        `lineIndex` INTEGER NOT NULL, 
                        `evidenceJson` TEXT NOT NULL, 
                        `correctionJson` TEXT, 
                        `isIgnored` INTEGER NOT NULL, 
                        PRIMARY KEY(`parseResultId`, `lineIndex`), 
                        FOREIGN KEY(`parseResultId`) REFERENCES `purchase_invoice_parse_results`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_parsed_lines_parseResultId` ON `purchase_invoice_parsed_lines` (`parseResultId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create supplier_item_mappings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `supplier_item_mappings` (
                        `id` TEXT NOT NULL, 
                        `restaurantId` TEXT NOT NULL, 
                        `supplierId` TEXT NOT NULL, 
                        `keyType` TEXT NOT NULL, 
                        `normalizedKey` TEXT NOT NULL, 
                        `sourceVendorCode` TEXT, 
                        `sourceDescription` TEXT, 
                        `sourcePackageText` TEXT, 
                        `ingredientId` TEXT NOT NULL, 
                        `unitOptionId` TEXT, 
                        `inventoryAreaId` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `lastConfirmedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`restaurantId`) REFERENCES `restaurants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`unitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, 
                        FOREIGN KEY(`inventoryAreaId`) REFERENCES `inventory_areas`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_item_mappings_restaurantId` ON `supplier_item_mappings` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_item_mappings_supplierId` ON `supplier_item_mappings` (`supplierId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_item_mappings_ingredientId` ON `supplier_item_mappings` (`ingredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_item_mappings_unitOptionId` ON `supplier_item_mappings` (`unitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_item_mappings_inventoryAreaId` ON `supplier_item_mappings` (`inventoryAreaId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_item_mappings_restaurantId_supplierId_keyType_normalizedKey` ON `supplier_item_mappings` (`restaurantId`, `supplierId`, `keyType`, `normalizedKey`)")

                // Create purchase_invoice_line_matches table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_line_matches` (
                        `parseResultId` TEXT NOT NULL, 
                        `lineIndex` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `supplierId` TEXT, 
                        `ingredientId` TEXT, 
                        `unitOptionId` TEXT, 
                        `inventoryAreaId` TEXT, 
                        `mappingId` TEXT, 
                        `matchMethod` TEXT, 
                        `matchConfidence` REAL NOT NULL, 
                        `confirmedAt` INTEGER, 
                        PRIMARY KEY(`parseResultId`, `lineIndex`), 
                        FOREIGN KEY(`parseResultId`) REFERENCES `purchase_invoice_parse_results`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, 
                        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, 
                        FOREIGN KEY(`unitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, 
                        FOREIGN KEY(`inventoryAreaId`) REFERENCES `inventory_areas`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, 
                        FOREIGN KEY(`mappingId`) REFERENCES `supplier_item_mappings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_parseResultId` ON `purchase_invoice_line_matches` (`parseResultId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_supplierId` ON `purchase_invoice_line_matches` (`supplierId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_ingredientId` ON `purchase_invoice_line_matches` (`ingredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_unitOptionId` ON `purchase_invoice_line_matches` (`unitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_inventoryAreaId` ON `purchase_invoice_line_matches` (`inventoryAreaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_matches_mappingId` ON `purchase_invoice_line_matches` (`mappingId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_draft_applications` (
                        `id` TEXT NOT NULL, 
                        `purchaseReceiptId` TEXT NOT NULL, 
                        `parseResultId` TEXT NOT NULL, 
                        `sourceDocumentSha256` TEXT NOT NULL, 
                        `sourceStateFingerprint` TEXT NOT NULL, 
                        `appliedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`purchaseReceiptId`) REFERENCES `purchase_receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`parseResultId`) REFERENCES `purchase_invoice_parse_results`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_draft_applications_purchaseReceiptId` ON `purchase_invoice_draft_applications` (`purchaseReceiptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_draft_applications_parseResultId` ON `purchase_invoice_draft_applications` (`parseResultId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_line_origins` (
                        `purchaseLineId` TEXT NOT NULL, 
                        `applicationId` TEXT NOT NULL, 
                        `sourceLineIndex` INTEGER NOT NULL, 
                        `sourceStateFingerprint` TEXT NOT NULL, 
                        `lastMaterializedSnapshotJson` TEXT NOT NULL, 
                        PRIMARY KEY(`purchaseLineId`), 
                        FOREIGN KEY(`purchaseLineId`) REFERENCES `purchase_lines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`applicationId`) REFERENCES `purchase_invoice_draft_applications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_line_origins_purchaseLineId` ON `purchase_invoice_line_origins` (`purchaseLineId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_line_origins_applicationId` ON `purchase_invoice_line_origins` (`applicationId`)")
            }
        }
    }
}
