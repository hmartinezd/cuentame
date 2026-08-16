package com.miara.cuentame.core.di

import com.miara.cuentame.core.database.repository.RoomDashboardRepository
import com.miara.cuentame.core.database.repository.RoomIngredientCategoryRepository
import com.miara.cuentame.core.database.repository.RoomIngredientRepository
import com.miara.cuentame.core.database.repository.RoomInventoryAreaRepository
import com.miara.cuentame.core.database.repository.RoomPreparationRecipeRepository
import com.miara.cuentame.core.database.repository.RoomInventoryReadRepository
import com.miara.cuentame.core.database.repository.RoomInventorySnapshotService
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.database.repository.RoomLocalSetupRepository
import com.miara.cuentame.core.database.repository.RoomRestaurantRepository
import com.miara.cuentame.core.database.repository.RoomStockCountRepository
import com.miara.cuentame.core.database.repository.RoomSupplierRepository
import com.miara.cuentame.core.database.repository.RoomUnitRepository
import com.miara.cuentame.core.database.repository.RoomWasteRepository
import com.miara.cuentame.core.database.repository.RoomProductionBatchRepository
import com.miara.cuentame.core.database.repository.RoomPreparationCostRepository
import com.miara.cuentame.core.database.repository.RoomMenuRecipeRepository
import com.miara.cuentame.core.database.repository.RoomMenuCatalogRepository
import com.miara.cuentame.core.database.repository.RoomMenuCostRepository
import com.miara.cuentame.core.database.repository.RoomInventoryActivityRepository
import com.miara.cuentame.core.database.repository.RoomSupplierItemMappingRepository
import com.miara.cuentame.core.database.repository.RoomCsvImportRepository
import com.miara.cuentame.core.database.repository.RoomPriceIntelligenceRepository
import com.miara.cuentame.core.domain.repository.ProductionBatchRepository
import com.miara.cuentame.core.domain.repository.PreparationCostRepository
import com.miara.cuentame.core.domain.repository.MenuRecipeRepository
import com.miara.cuentame.core.domain.repository.MenuCatalogRepository
import com.miara.cuentame.core.domain.repository.MenuCostRepository
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.miara.cuentame.core.domain.repository.CsvImportRepository
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.InventoryReadRepository
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.repository.PriceIntelligenceRepository
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
    abstract fun bindInventorySnapshotService(repo: RoomInventorySnapshotService): com.miara.cuentame.core.domain.service.InventorySnapshotService

    @Binds
    @Singleton
    abstract fun bindWasteRepository(repo: RoomWasteRepository): WasteRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(repo: RoomDashboardRepository): com.miara.cuentame.core.domain.repository.DashboardRepository

    @Binds
    @Singleton
    abstract fun bindPreparationRecipeRepository(repo: RoomPreparationRecipeRepository): PreparationRecipeRepository

    @Binds
    @Singleton
    abstract fun bindPreparationCostRepository(repo: RoomPreparationCostRepository): PreparationCostRepository

    @Binds @Singleton
    abstract fun bindMenuRecipeRepository(repo: RoomMenuRecipeRepository): MenuRecipeRepository

    @Binds @Singleton
    abstract fun bindMenuCatalogRepository(repo: RoomMenuCatalogRepository): MenuCatalogRepository

    @Binds @Singleton
    abstract fun bindMenuCostRepository(repo: RoomMenuCostRepository): MenuCostRepository

    @Binds
    @Singleton
    abstract fun bindProductionBatchRepository(repo: RoomProductionBatchRepository): ProductionBatchRepository

    @Binds
    @Singleton
    abstract fun bindInventoryActivityRepository(repo: RoomInventoryActivityRepository): InventoryActivityRepository

    @Binds
    @Singleton
    abstract fun bindSupplierItemMappingRepository(repo: RoomSupplierItemMappingRepository): SupplierItemMappingRepository

    @Binds
    @Singleton
    abstract fun bindCsvImportRepository(repo: RoomCsvImportRepository): CsvImportRepository

    @Binds
    @Singleton
    abstract fun bindPriceIntelligenceRepository(repo: RoomPriceIntelligenceRepository): PriceIntelligenceRepository
}
