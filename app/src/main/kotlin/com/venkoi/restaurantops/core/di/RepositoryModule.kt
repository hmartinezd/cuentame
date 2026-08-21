package com.venkoi.restaurantops.core.di

import com.venkoi.restaurantops.core.database.repository.RoomDashboardRepository
import com.venkoi.restaurantops.core.database.repository.RoomIngredientCategoryRepository
import com.venkoi.restaurantops.core.database.repository.RoomIngredientRepository
import com.venkoi.restaurantops.core.database.repository.RoomInventoryAreaRepository
import com.venkoi.restaurantops.core.database.repository.RoomPreparationRecipeRepository
import com.venkoi.restaurantops.core.database.repository.RoomInventoryReadRepository
import com.venkoi.restaurantops.core.database.repository.RoomInventorySnapshotService
import com.venkoi.restaurantops.core.database.repository.RoomPurchaseRepository
import com.venkoi.restaurantops.core.database.repository.RoomLocalSetupRepository
import com.venkoi.restaurantops.core.database.repository.RoomRestaurantRepository
import com.venkoi.restaurantops.core.database.repository.RoomStockCountRepository
import com.venkoi.restaurantops.core.database.repository.RoomSupplierRepository
import com.venkoi.restaurantops.core.database.repository.RoomUnitRepository
import com.venkoi.restaurantops.core.database.repository.RoomWasteRepository
import com.venkoi.restaurantops.core.database.repository.RoomProductionBatchRepository
import com.venkoi.restaurantops.core.database.repository.RoomPreparationCostRepository
import com.venkoi.restaurantops.core.database.repository.RoomMenuRecipeRepository
import com.venkoi.restaurantops.core.database.repository.RoomMenuItemCreationRepository
import com.venkoi.restaurantops.core.database.repository.RoomMenuCatalogRepository
import com.venkoi.restaurantops.core.database.repository.RoomMenuCostRepository
import com.venkoi.restaurantops.core.database.repository.RoomInventoryActivityRepository
import com.venkoi.restaurantops.core.database.repository.RoomSupplierItemMappingRepository
import com.venkoi.restaurantops.core.database.repository.RoomCsvImportRepository
import com.venkoi.restaurantops.core.database.repository.RoomPriceIntelligenceRepository
import com.venkoi.restaurantops.core.domain.repository.ProductionBatchRepository
import com.venkoi.restaurantops.core.domain.repository.PreparationCostRepository
import com.venkoi.restaurantops.core.domain.repository.MenuRecipeRepository
import com.venkoi.restaurantops.core.domain.repository.MenuItemCreationRepository
import com.venkoi.restaurantops.core.domain.repository.MenuCatalogRepository
import com.venkoi.restaurantops.core.domain.repository.MenuCostRepository
import com.venkoi.restaurantops.core.domain.repository.InventoryActivityRepository
import com.venkoi.restaurantops.core.domain.repository.SupplierItemMappingRepository
import com.venkoi.restaurantops.core.domain.repository.CsvImportRepository
import com.venkoi.restaurantops.core.domain.repository.WasteRepository
import com.venkoi.restaurantops.core.domain.repository.IngredientCategoryRepository
import com.venkoi.restaurantops.core.domain.repository.IngredientRepository
import com.venkoi.restaurantops.core.domain.repository.InventoryAreaRepository
import com.venkoi.restaurantops.core.domain.repository.InventoryReadRepository
import com.venkoi.restaurantops.core.domain.repository.LocalSetupRepository
import com.venkoi.restaurantops.core.domain.repository.PreparationRecipeRepository
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.repository.StockCountRepository
import com.venkoi.restaurantops.core.domain.repository.SupplierRepository
import com.venkoi.restaurantops.core.domain.repository.UnitRepository
import com.venkoi.restaurantops.core.domain.repository.PriceIntelligenceRepository
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
    abstract fun bindInventorySnapshotService(repo: RoomInventorySnapshotService): com.venkoi.restaurantops.core.domain.service.InventorySnapshotService

    @Binds
    @Singleton
    abstract fun bindWasteRepository(repo: RoomWasteRepository): WasteRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(repo: RoomDashboardRepository): com.venkoi.restaurantops.core.domain.repository.DashboardRepository

    @Binds
    @Singleton
    abstract fun bindPreparationRecipeRepository(repo: RoomPreparationRecipeRepository): PreparationRecipeRepository

    @Binds
    @Singleton
    abstract fun bindPreparationCostRepository(repo: RoomPreparationCostRepository): PreparationCostRepository

    @Binds @Singleton
    abstract fun bindMenuRecipeRepository(repo: RoomMenuRecipeRepository): MenuRecipeRepository

    @Binds
    abstract fun bindMenuItemCreationRepository(repo: RoomMenuItemCreationRepository): MenuItemCreationRepository

    @Binds @Singleton
    abstract fun bindMenuCatalogRepository(repo: RoomMenuCatalogRepository): MenuCatalogRepository

    @Binds @Singleton
    abstract fun bindMenuPublicationRepository(repo: com.venkoi.restaurantops.core.database.repository.RoomMenuPublicationRepository): com.venkoi.restaurantops.core.domain.repository.MenuPublicationRepository

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

    @Binds @Singleton
    abstract fun bindSalesImportRepository(repo: com.venkoi.restaurantops.core.database.repository.RoomSalesImportRepository): com.venkoi.restaurantops.core.domain.repository.SalesImportRepository
}
