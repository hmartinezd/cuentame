package com.venkoi.cuentame.test.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.venkoi.cuentame.core.backup.api.RestoreStartupState
import com.venkoi.cuentame.core.backup.internal.RecoveryBootstrapper
import com.venkoi.cuentame.core.backup.internal.RecoveryModule
import com.venkoi.cuentame.core.backup.internal.RestoreOperationGate
import com.venkoi.cuentame.core.common.attachment.LocalAttachmentPermissionManager
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.dao.*
import com.venkoi.cuentame.core.database.di.DatabaseModule
import com.venkoi.cuentame.core.database.repository.ConfigurableFailureBoundary
import com.venkoi.cuentame.core.database.repository.IntegrationFailureBoundary
import com.venkoi.cuentame.core.database.repository.RoomDetailedReportsRepository
import com.venkoi.cuentame.core.di.IntegrationModule
import com.venkoi.cuentame.core.di.LocalAttachmentModule
import com.venkoi.cuentame.core.domain.repository.DetailedReportsRepository
import com.venkoi.cuentame.core.preferences.di.PreferencesModule
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import com.venkoi.cuentame.core.preferences.datastore.DataStoreAppPreferencesRepository
import com.venkoi.cuentame.test.ConfigurableAttachmentPermissionManager
import com.venkoi.cuentame.test.TestDataStoreOwner
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [
        DatabaseModule::class, 
        PreferencesModule::class,
        IntegrationModule::class,
        LocalAttachmentModule::class,
        RecoveryModule::class
    ]
)
object TestStorageModule {

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): RestaurantInventoryDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                com.venkoi.cuentame.core.database.seed.SystemUnitSeeder.seed(db)
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                com.venkoi.cuentame.core.database.seed.SystemUnitSeeder.seed(db)
            }
        })
        .build()
    }

    @Provides
    fun provideRestaurantDao(db: RestaurantInventoryDatabase) = db.restaurantDao()
    @Provides
    fun provideInventoryAreaDao(db: RestaurantInventoryDatabase) = db.inventoryAreaDao()
    @Provides
    fun provideIngredientCategoryDao(db: RestaurantInventoryDatabase) = db.ingredientCategoryDao()
    @Provides
    fun provideUnitDao(db: RestaurantInventoryDatabase) = db.unitDao()
    @Provides
    fun provideIngredientDao(db: RestaurantInventoryDatabase) = db.ingredientDao()
    @Provides
    fun provideIngredientUnitOptionDao(db: RestaurantInventoryDatabase) = db.ingredientUnitOptionDao()
    @Provides
    fun provideSupplierDao(db: RestaurantInventoryDatabase) = db.supplierDao()
    @Provides
    fun providePurchaseDao(db: RestaurantInventoryDatabase) = db.purchaseDao()
    @Provides
    fun provideStockCountDao(db: RestaurantInventoryDatabase) = db.stockCountDao()
    @Provides
    fun provideWasteDao(db: RestaurantInventoryDatabase) = db.wasteDao()
    @Provides
    fun provideInventoryMovementDao(db: RestaurantInventoryDatabase) = db.inventoryMovementDao()
    @Provides
    fun provideInventoryProjectionDao(db: RestaurantInventoryDatabase) = db.inventoryProjectionDao()
    @Provides
    fun provideIngredientCostProjectionDao(db: RestaurantInventoryDatabase) = db.ingredientCostProjectionDao()
    @Provides
    fun providePreparationRecipeDao(db: RestaurantInventoryDatabase) = db.preparationRecipeDao()
    @Provides
    fun provideMenuRecipeDao(db: RestaurantInventoryDatabase) = db.menuRecipeDao()
    @Provides
    fun provideMenuCatalogDao(db: RestaurantInventoryDatabase) = db.menuCatalogDao()
    @Provides
    fun provideMenuPublicationDao(db: RestaurantInventoryDatabase) = db.menuPublicationDao()
    @Provides
    fun provideProductionBatchDao(db: RestaurantInventoryDatabase) = db.productionBatchDao()
    @Provides
    fun providePurchaseOcrDao(db: RestaurantInventoryDatabase) = db.purchaseOcrDao()
    @Provides
    fun providePurchaseParseDao(db: RestaurantInventoryDatabase) = db.purchaseParseDao()
    @Provides
    fun provideSupplierItemMappingDao(db: RestaurantInventoryDatabase) = db.supplierItemMappingDao()
    @Provides
    fun providePurchaseInvoiceLineMatchDao(db: RestaurantInventoryDatabase) = db.purchaseInvoiceLineMatchDao()
    @Provides
    fun providePurchaseInvoiceMaterializationDao(db: RestaurantInventoryDatabase) = db.purchaseInvoiceMaterializationDao()
    @Provides
    fun provideBackupDao(db: RestaurantInventoryDatabase) = db.backupDao()
    @Provides
    fun provideRestoreDao(db: RestaurantInventoryDatabase) = db.restoreDao()
    @Provides
    fun provideSalesImportDao(db: RestaurantInventoryDatabase) = db.salesImportDao()

    @Provides
    fun provideDetailedReportsRepository(
        inventoryProjectionDao: InventoryProjectionDao,
        purchaseDao: PurchaseDao,
        movementDao: InventoryMovementDao
    ): DetailedReportsRepository = RoomDetailedReportsRepository(
        inventoryProjectionDao,
        purchaseDao,
        movementDao
    )

    @Provides
    @Singleton
    fun provideDataStore(owner: TestDataStoreOwner): DataStore<Preferences> = owner.dataStore

    @Provides
    @Singleton
    fun provideAppPreferencesRepository(
        dataStore: DataStore<Preferences>
    ): AppPreferencesRepository {
        return DataStoreAppPreferencesRepository(
            dataStore,
            Json { 
                ignoreUnknownKeys = false 
                coerceInputValues = true
            }
        )
    }

    @Provides
    @Singleton
    fun provideConfigurableFailureBoundary(): ConfigurableFailureBoundary = ConfigurableFailureBoundary()

    @Provides
    @Singleton
    fun provideIntegrationFailureBoundary(configurable: ConfigurableFailureBoundary): IntegrationFailureBoundary = configurable

    @Provides
    @Singleton
    fun provideLocalAttachmentPermissionManager(): LocalAttachmentPermissionManager = ConfigurableAttachmentPermissionManager()

    @Provides
    @Singleton
    fun provideRecoveryBootstrapper(): RecoveryBootstrapper = object : RecoveryBootstrapper {
        override fun bootstrap() {
            // No-op for integration tests to avoid deadlock with test setup
        }
    }
}
