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
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.dao.WasteDao
import com.miara.cuentame.core.database.seed.UnitSeeds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        unitDaoProvider: Provider<UnitDao>
    ): RestaurantInventoryDatabase {
        return Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            "restaurant_inventory.db"
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // We use a coroutine scope to seed units after creation
                CoroutineScope(Dispatchers.IO).launch {
                    unitDaoProvider.get().insertSeedUnits(UnitSeeds.ALL_UNITS)
                }
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
}
