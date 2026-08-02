package com.miara.cuentame.test.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.di.DatabaseModule
import com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
import com.miara.cuentame.core.database.repository.IntegrationFailureBoundary
import com.miara.cuentame.core.database.repository.RoomDetailedReportsRepository
import com.miara.cuentame.core.di.IntegrationModule
import com.miara.cuentame.core.di.LocalAttachmentModule
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.preferences.di.PreferencesModule
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.core.preferences.datastore.DataStoreAppPreferencesRepository
import com.miara.cuentame.test.ConfigurableAttachmentPermissionManager
import com.miara.cuentame.test.TestDataStoreOwner
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
        LocalAttachmentModule::class
    ]
)
object TestStorageModule {

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): RestaurantInventoryDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
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
    fun provideProductionBatchDao(db: RestaurantInventoryDatabase) = db.productionBatchDao()
    @Provides
    fun provideBackupDao(db: RestaurantInventoryDatabase) = db.backupDao()
    @Provides
    fun provideRestoreDao(db: RestaurantInventoryDatabase) = db.restoreDao()

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
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = false
        coerceInputValues = true
    }
}
