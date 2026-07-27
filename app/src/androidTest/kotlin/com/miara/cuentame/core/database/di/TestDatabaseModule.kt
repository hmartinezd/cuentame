package com.miara.cuentame.core.database.di

import android.content.Context
import androidx.room.Room
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.repository.RoomDetailedReportsRepository
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RestaurantInventoryDatabase {
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
    fun provideBackupDao(db: RestaurantInventoryDatabase) = db.backupDao()

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
}
