package com.venkoi.restaurantops.core.di

import com.venkoi.restaurantops.core.common.AndroidDeviceInfoProvider
import com.venkoi.restaurantops.core.common.DeviceInfoProvider
import com.venkoi.restaurantops.core.database.repository.NoOpFailureBoundary
import com.venkoi.restaurantops.core.database.repository.IntegrationFailureBoundary
import com.venkoi.restaurantops.core.common.attachment.AndroidLocalAttachmentPermissionManager
import com.venkoi.restaurantops.core.common.attachment.LocalAttachmentPermissionManager
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.ids.UuidIdGenerator
import com.venkoi.restaurantops.core.common.time.SystemTimeProvider
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.domain.service.CountAdjustmentCalculator
import com.venkoi.restaurantops.core.domain.service.CountComparisonCalculator
import com.venkoi.restaurantops.core.domain.service.IngredientUnitConverter
import com.venkoi.restaurantops.core.domain.service.InventoryBalanceCalculator
import com.venkoi.restaurantops.core.domain.service.StandardUnitConverter
import com.venkoi.restaurantops.core.domain.service.HistoricalInventoryCostCalculator
import com.venkoi.restaurantops.core.domain.service.InventoryMovementService
import com.venkoi.restaurantops.core.domain.service.PurchaseLineCalculator
import com.venkoi.restaurantops.core.domain.service.InventorySnapshotService
import com.venkoi.restaurantops.core.database.repository.InventoryMovementValidator
import com.venkoi.restaurantops.core.database.repository.PurchaseMovementHistoryValidator
import com.venkoi.restaurantops.core.database.repository.PurchaseReferenceValidator
import com.venkoi.restaurantops.core.database.repository.StockCountMovementHistoryValidator
import com.venkoi.restaurantops.core.database.repository.WasteMovementHistoryValidator
import com.venkoi.restaurantops.core.database.repository.RoomInventorySnapshotService
import com.venkoi.restaurantops.core.domain.usecase.LocalSetupValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun provideDeviceInfoProvider(): DeviceInfoProvider = AndroidDeviceInfoProvider()

    @Provides
    @Singleton
    fun provideInventoryMovementService(
        idGenerator: IdGenerator,
        timeProvider: TimeProvider
    ): InventoryMovementService = InventoryMovementService(idGenerator, timeProvider)

    @Provides
    @Singleton
    fun provideStandardUnitConverter(): StandardUnitConverter = StandardUnitConverter()

    @Provides
    @Singleton
    fun provideIngredientUnitConverter(): IngredientUnitConverter = IngredientUnitConverter()

    @Provides
    @Singleton
    fun provideInventoryBalanceCalculator(): InventoryBalanceCalculator = InventoryBalanceCalculator()

    @Provides
    @Singleton
    fun provideHistoricalInventoryCostCalculator(): HistoricalInventoryCostCalculator = HistoricalInventoryCostCalculator()

    @Provides
    @Singleton
    fun provideCountAdjustmentCalculator(): CountAdjustmentCalculator = CountAdjustmentCalculator()

    @Provides
    @Singleton
    fun provideCountComparisonCalculator(): CountComparisonCalculator = CountComparisonCalculator()

    @Provides
    @Singleton
    fun provideLocalSetupValidator(): LocalSetupValidator = LocalSetupValidator()

    @Provides
    @Singleton
    fun providePurchaseLineCalculator(): PurchaseLineCalculator = PurchaseLineCalculator()

    @Provides
    @Singleton
    fun providePurchaseMovementHistoryValidator(): PurchaseMovementHistoryValidator = PurchaseMovementHistoryValidator()

    @Provides
    @Singleton
    fun provideStockCountMovementHistoryValidator(): StockCountMovementHistoryValidator = StockCountMovementHistoryValidator()

    @Provides
    @Singleton
    fun provideWasteMovementHistoryValidator(
        movementValidator: InventoryMovementValidator
    ): WasteMovementHistoryValidator = WasteMovementHistoryValidator(movementValidator)

    @Provides
    @Singleton
    fun provideInventoryMovementValidator(): InventoryMovementValidator = InventoryMovementValidator()

    @Provides
    fun providePurchaseReferenceValidator(
        purchaseDao: com.venkoi.restaurantops.core.database.dao.PurchaseDao,
        supplierDao: com.venkoi.restaurantops.core.database.dao.SupplierDao,
        ingredientDao: com.venkoi.restaurantops.core.database.dao.IngredientDao,
        areaDao: com.venkoi.restaurantops.core.database.dao.InventoryAreaDao,
        unitOptionDao: com.venkoi.restaurantops.core.database.dao.IngredientUnitOptionDao
    ): PurchaseReferenceValidator = PurchaseReferenceValidator(
        purchaseDao, supplierDao, ingredientDao, areaDao, unitOptionDao
    )
}
