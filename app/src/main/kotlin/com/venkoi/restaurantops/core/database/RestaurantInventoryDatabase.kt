package com.venkoi.restaurantops.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.venkoi.restaurantops.core.database.dao.*
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.common.database.DatabaseSchema

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
        StockCountItemOrderEntity::class,
        WasteEventEntity::class,
        InventoryMovementEntity::class,
        InventoryBalanceProjectionEntity::class,
        IngredientCostProjectionEntity::class,
        PreparationRecipeEntity::class,
        PreparationRecipeComponentEntity::class,
        MenuRecipeEntity::class,
        MenuRecipeComponentEntity::class,
        MenuEntity::class,
        MenuCategoryEntity::class,
        MenuPlacementEntity::class,
        MenuPublicationEntity::class,
        MenuPublicationCategoryEntity::class,
        MenuPublicationItemEntity::class,
        MenuPublicationItemComponentEntity::class,
        ProductionBatchEntity::class,
        ProductionBatchComponentEntity::class,
        PurchaseInvoiceOcrResultEntity::class,
        PurchaseInvoiceOcrPageEntity::class,
        PurchaseInvoiceParseResultEntity::class,
        PurchaseInvoiceParsedLineEntity::class,
        SupplierItemMappingEntity::class,
        PurchaseInvoiceLineMatchEntity::class,
        PurchaseInvoiceDraftApplicationEntity::class,
        PurchaseInvoiceLineOriginEntity::class,
        SalesImportEntity::class,
        ImportedSaleTransactionEntity::class,
        ImportedSaleLineEntity::class,
        SalesImportTransactionRefEntity::class
    ],
    version = DatabaseSchema.VERSION,
    exportSchema = true
)
@TypeConverters(com.venkoi.restaurantops.core.database.converter.RoomTypeConverters::class)
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
    abstract fun menuRecipeDao(): MenuRecipeDao
    abstract fun menuCatalogDao(): MenuCatalogDao
    abstract fun menuPublicationDao(): MenuPublicationDao
    abstract fun productionBatchDao(): ProductionBatchDao
    abstract fun purchaseOcrDao(): PurchaseOcrDao
    abstract fun purchaseParseDao(): PurchaseParseDao
    abstract fun supplierItemMappingDao(): SupplierItemMappingDao
    abstract fun purchaseInvoiceLineMatchDao(): PurchaseInvoiceLineMatchDao
    abstract fun purchaseInvoiceMaterializationDao(): PurchaseInvoiceMaterializationDao
    abstract fun backupDao(): BackupDao
    abstract fun restoreDao(): RestoreDao
    abstract fun salesImportDao(): SalesImportDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ingredient_cost_projection_new` (`restaurantId` TEXT NOT NULL, `ingredientId` TEXT NOT NULL, `averageUnitCostBase` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`restaurantId`, `ingredientId`))")
                db.execSQL("INSERT INTO `ingredient_cost_projection_new` (`restaurantId`, `ingredientId`, `averageUnitCostBase`, `updatedAt`) SELECT `restaurantId`, `ingredientId`, `averageUnitCostBase`, `updatedAt` FROM `ingredient_cost_projection`")
                db.execSQL("DROP TABLE `ingredient_cost_projection`")
                db.execSQL("ALTER TABLE `ingredient_cost_projection_new` RENAME TO `ingredient_cost_projection`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_cost_projection_restaurantId` ON `ingredient_cost_projection` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_cost_projection_ingredientId` ON `ingredient_cost_projection` (`ingredientId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId` ON `preparation_recipes` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_outputIngredientId` ON `preparation_recipes` (`outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_yieldUnitOptionId` ON `preparation_recipes` (`yieldUnitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId_outputIngredientId` ON `preparation_recipes` (`restaurantId`, `outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipes_restaurantId_status` ON `preparation_recipes` (`restaurantId`, `status`)")

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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_recipeId` ON `preparation_recipe_components` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_componentIngredientId` ON `preparation_recipe_components` (`componentIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_preparation_recipe_components_unitOptionId` ON `preparation_recipe_components` (`unitOptionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_preparation_recipe_components_recipeId_componentIngredientId` ON `preparation_recipe_components` (`recipeId`, `componentIngredientId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId` ON `production_batches` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_recipeId` ON `production_batches` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputIngredientId` ON `production_batches` (`outputIngredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputAreaId` ON `production_batches` (`outputAreaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_outputUnitOptionId` ON `production_batches` (`outputUnitOptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_status` ON `production_batches` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_effectiveAt` ON `production_batches` (`effectiveAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId_effectiveAt` ON `production_batches` (`restaurantId`, `effectiveAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_restaurantId_status` ON `production_batches` (`restaurantId`, `status`)")

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

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoice_line_origins_applicationId_sourceLineIndex` ON `purchase_invoice_line_origins` (`applicationId`, `sourceLineIndex`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `menu_recipes` (
                        `id` TEXT NOT NULL, 
                        `restaurantId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `normalizedName` TEXT NOT NULL, 
                        `sellingPrice` TEXT, 
                        `notes` TEXT, 
                        `archivedAt` INTEGER, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`restaurantId`) REFERENCES `restaurants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipes_restaurantId` ON `menu_recipes` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipes_restaurantId_normalizedName` ON `menu_recipes` (`restaurantId`, `normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipes_archivedAt` ON `menu_recipes` (`archivedAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `menu_recipe_components` (
                        `id` TEXT NOT NULL, 
                        `menuRecipeId` TEXT NOT NULL, 
                        `ingredientId` TEXT NOT NULL, 
                        `ingredientUnitOptionId` TEXT NOT NULL, 
                        `quantityEntered` TEXT NOT NULL, 
                        `quantityBase` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`menuRecipeId`) REFERENCES `menu_recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, 
                        FOREIGN KEY(`ingredientUnitOptionId`) REFERENCES `ingredient_unit_options`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipe_components_menuRecipeId` ON `menu_recipe_components` (`menuRecipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipe_components_ingredientId` ON `menu_recipe_components` (`ingredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_recipe_components_ingredientUnitOptionId` ON `menu_recipe_components` (`ingredientUnitOptionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_menu_recipe_components_menuRecipeId_ingredientId` ON `menu_recipe_components` (`menuRecipeId`, `ingredientId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stock_count_item_order` (
                        `restaurantId` TEXT NOT NULL, 
                        `areaId` TEXT NOT NULL, 
                        `ingredientId` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`areaId`, `ingredientId`), 
                        FOREIGN KEY(`restaurantId`) REFERENCES `restaurants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`areaId`) REFERENCES `inventory_areas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_count_item_order_restaurantId` ON `stock_count_item_order` (`restaurantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_count_item_order_ingredientId` ON `stock_count_item_order` (`ingredientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_count_item_order_areaId_sortOrder` ON `stock_count_item_order` (`areaId`, `sortOrder`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ingredients` ADD COLUMN `parLevelBase` TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `purchase_invoice_draft_applications` ADD COLUMN `duplicateOverrideType` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoice_draft_applications` ADD COLUMN `duplicateExistingReceiptId` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoice_draft_applications` ADD COLUMN `duplicateNormalizedInvoiceNumber` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoice_draft_applications` ADD COLUMN `duplicateSourceSha256` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoice_draft_applications` ADD COLUMN `duplicateOverriddenAt` INTEGER")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
        )
    }
}
