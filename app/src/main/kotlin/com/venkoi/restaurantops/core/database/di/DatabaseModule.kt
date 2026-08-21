package com.venkoi.restaurantops.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.dao.IngredientCategoryDao
import com.venkoi.restaurantops.core.database.dao.IngredientCostProjectionDao
import com.venkoi.restaurantops.core.database.dao.IngredientDao
import com.venkoi.restaurantops.core.database.dao.IngredientUnitOptionDao
import com.venkoi.restaurantops.core.database.dao.InventoryAreaDao
import com.venkoi.restaurantops.core.database.dao.InventoryMovementDao
import com.venkoi.restaurantops.core.database.dao.InventoryProjectionDao
import com.venkoi.restaurantops.core.database.dao.PreparationRecipeDao
import com.venkoi.restaurantops.core.database.dao.MenuRecipeDao
import com.venkoi.restaurantops.core.database.dao.MenuCatalogDao
import com.venkoi.restaurantops.core.database.dao.MenuPublicationDao
import com.venkoi.restaurantops.core.database.dao.ProductionBatchDao
import com.venkoi.restaurantops.core.database.dao.PurchaseOcrDao
import com.venkoi.restaurantops.core.database.dao.PurchaseParseDao
import com.venkoi.restaurantops.core.database.dao.SupplierItemMappingDao
import com.venkoi.restaurantops.core.database.dao.PurchaseInvoiceLineMatchDao
import com.venkoi.restaurantops.core.database.dao.PurchaseInvoiceMaterializationDao
import com.venkoi.restaurantops.core.database.dao.BackupDao
import com.venkoi.restaurantops.core.database.dao.RestoreDao
import com.venkoi.restaurantops.core.database.dao.PurchaseDao
import com.venkoi.restaurantops.core.database.dao.RestaurantDao
import com.venkoi.restaurantops.core.database.dao.StockCountDao
import com.venkoi.restaurantops.core.database.dao.SupplierDao
import com.venkoi.restaurantops.core.database.dao.UnitDao
import com.venkoi.restaurantops.core.database.dao.WasteDao
import com.venkoi.restaurantops.core.database.seed.SystemUnitSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RestaurantInventoryDatabase {
        return Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            "restaurant_inventory.db"
        )
        .addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        )
        // Pre-release schema policy: version 11 introduces count-order configuration.
        // Development databases are intentionally recreated; no unpublished-data migration.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SystemUnitSeeder.seed(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                SystemUnitSeeder.seed(db)
            }
        }).build()
    }

    @Provides
    fun provideRestaurantDao(db: RestaurantInventoryDatabase): RestaurantDao = db.restaurantDao()

    @Provides
    fun provideInventoryAreaDao(db: RestaurantInventoryDatabase): InventoryAreaDao = db.inventoryAreaDao()

    @Provides
    fun provideIngredientCategoryDao(db: RestaurantInventoryDatabase): IngredientCategoryDao = db.ingredientCategoryDao()

    @Provides
    fun provideUnitDao(db: RestaurantInventoryDatabase): UnitDao = db.unitDao()

    @Provides
    fun provideIngredientDao(db: RestaurantInventoryDatabase): IngredientDao = db.ingredientDao()

    @Provides
    fun provideMenuCatalogDao(db: RestaurantInventoryDatabase): MenuCatalogDao = db.menuCatalogDao()

    @Provides
    fun provideMenuPublicationDao(db: RestaurantInventoryDatabase): MenuPublicationDao = db.menuPublicationDao()

    @Provides
    fun provideIngredientUnitOptionDao(db: RestaurantInventoryDatabase): IngredientUnitOptionDao = db.ingredientUnitOptionDao()

    @Provides
    fun provideSupplierDao(db: RestaurantInventoryDatabase): SupplierDao = db.supplierDao()

    @Provides
    fun providePurchaseDao(db: RestaurantInventoryDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideStockCountDao(db: RestaurantInventoryDatabase): StockCountDao = db.stockCountDao()

    @Provides
    fun provideWasteDao(db: RestaurantInventoryDatabase): WasteDao = db.wasteDao()

    @Provides
    fun provideInventoryMovementDao(db: RestaurantInventoryDatabase): InventoryMovementDao = db.inventoryMovementDao()

    @Provides
    fun provideInventoryProjectionDao(db: RestaurantInventoryDatabase): InventoryProjectionDao = db.inventoryProjectionDao()

    @Provides
    fun provideIngredientCostProjectionDao(db: RestaurantInventoryDatabase): IngredientCostProjectionDao = db.ingredientCostProjectionDao()

    @Provides
    fun providePreparationRecipeDao(db: RestaurantInventoryDatabase): PreparationRecipeDao = db.preparationRecipeDao()

    @Provides
    fun provideMenuRecipeDao(db: RestaurantInventoryDatabase): MenuRecipeDao = db.menuRecipeDao()

    @Provides
    fun provideProductionBatchDao(db: RestaurantInventoryDatabase): ProductionBatchDao = db.productionBatchDao()

    @Provides
    fun providePurchaseOcrDao(db: RestaurantInventoryDatabase): PurchaseOcrDao = db.purchaseOcrDao()

    @Provides
    fun providePurchaseParseDao(db: RestaurantInventoryDatabase): PurchaseParseDao = db.purchaseParseDao()

    @Provides
    fun provideSupplierItemMappingDao(db: RestaurantInventoryDatabase): SupplierItemMappingDao = db.supplierItemMappingDao()

    @Provides
    fun providePurchaseInvoiceLineMatchDao(db: RestaurantInventoryDatabase): PurchaseInvoiceLineMatchDao = db.purchaseInvoiceLineMatchDao()

    @Provides
    fun providePurchaseInvoiceMaterializationDao(db: RestaurantInventoryDatabase): PurchaseInvoiceMaterializationDao = db.purchaseInvoiceMaterializationDao()

    @Provides
    fun provideBackupDao(db: RestaurantInventoryDatabase): BackupDao = db.backupDao()

    @Provides
    fun provideRestoreDao(db: RestaurantInventoryDatabase): RestoreDao = db.restoreDao()

    @Provides
    fun provideSalesImportDao(db: RestaurantInventoryDatabase): com.venkoi.restaurantops.core.database.dao.SalesImportDao = db.salesImportDao()

    @Provides
    fun provideDetailedReportsRepository(
        inventoryProjectionDao: InventoryProjectionDao,
        purchaseDao: PurchaseDao,
        movementDao: InventoryMovementDao
    ): com.venkoi.restaurantops.core.domain.repository.DetailedReportsRepository =
        com.venkoi.restaurantops.core.database.repository.RoomDetailedReportsRepository(
            inventoryProjectionDao,
            purchaseDao,
            movementDao
        )
}
