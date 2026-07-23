package com.miara.cuentame.core.di

import com.miara.cuentame.core.database.repository.RoomIngredientCategoryRepository
import com.miara.cuentame.core.database.repository.RoomIngredientRepository
import com.miara.cuentame.core.database.repository.RoomInventoryAreaRepository
import com.miara.cuentame.core.database.repository.RoomInventoryReadRepository
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.database.repository.RoomLocalSetupRepository
import com.miara.cuentame.core.database.repository.RoomRestaurantRepository
import com.miara.cuentame.core.database.repository.RoomStockCountRepository
import com.miara.cuentame.core.database.repository.RoomSupplierRepository
import com.miara.cuentame.core.database.repository.RoomUnitRepository
import com.miara.cuentame.core.database.repository.RoomWasteDraftRepository
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.InventoryReadRepository
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.repository.WasteDraftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRestaurantRepository(repo: RoomRestaurantRepository): RestaurantRepository

    @Binds
    @Singleton
    abstract fun bindLocalSetupRepository(repo: RoomLocalSetupRepository): LocalSetupRepository

    @Binds
    @Singleton
    abstract fun bindInventoryAreaRepository(repo: RoomInventoryAreaRepository): InventoryAreaRepository

    @Binds
    @Singleton
    abstract fun bindIngredientCategoryRepository(repo: RoomIngredientCategoryRepository): IngredientCategoryRepository

    @Binds
    @Singleton
    abstract fun bindUnitRepository(repo: RoomUnitRepository): UnitRepository

    @Binds
    @Singleton
    abstract fun bindIngredientRepository(repo: RoomIngredientRepository): IngredientRepository

    @Binds
    @Singleton
    abstract fun bindSupplierRepository(repo: RoomSupplierRepository): SupplierRepository

    @Binds
    @Singleton
    abstract fun bindInventoryReadRepository(repo: RoomInventoryReadRepository): InventoryReadRepository

    @Binds
    @Singleton
    abstract fun bindPurchaseRepository(repo: RoomPurchaseRepository): PurchaseRepository

    @Binds
    @Singleton
    abstract fun bindStockCountRepository(repo: RoomStockCountRepository): StockCountRepository

    @Binds
    @Singleton
    abstract fun bindWasteDraftRepository(repo: RoomWasteDraftRepository): WasteDraftRepository
}
