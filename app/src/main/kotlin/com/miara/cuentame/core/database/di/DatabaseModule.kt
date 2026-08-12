package com.miara.cuentame.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.dao.PreparationRecipeDao
import com.miara.cuentame.core.database.dao.MenuRecipeDao
import com.miara.cuentame.core.database.dao.ProductionBatchDao
import com.miara.cuentame.core.database.dao.PurchaseOcrDao
import com.miara.cuentame.core.database.dao.PurchaseParseDao
import com.miara.cuentame.core.database.dao.SupplierItemMappingDao
import com.miara.cuentame.core.database.dao.PurchaseInvoiceLineMatchDao
import com.miara.cuentame.core.database.dao.PurchaseInvoiceMaterializationDao
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.dao.RestoreDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.dao.WasteDao
import com.miara.cuentame.core.database.seed.SystemUnitSeeder
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
            RestaurantInventoryDatabase.MIGRATION_9_10
        )
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
    fun provideDetailedReportsRepository(
        inventoryProjectionDao: InventoryProjectionDao,
        purchaseDao: PurchaseDao,
        movementDao: InventoryMovementDao
    ): com.miara.cuentame.core.domain.repository.DetailedReportsRepository =
        com.miara.cuentame.core.database.repository.RoomDetailedReportsRepository(
            inventoryProjectionDao,
            purchaseDao,
            movementDao
        )
}
