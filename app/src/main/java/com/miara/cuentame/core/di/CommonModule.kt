package com.miara.cuentame.core.di

import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.UuidIdGenerator
import com.miara.cuentame.core.common.time.SystemTimeProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.service.CountAdjustmentCalculator
import com.miara.cuentame.core.domain.service.CountComparisonCalculator
import com.miara.cuentame.core.domain.service.IngredientUnitConverter
import com.miara.cuentame.core.domain.service.InventoryBalanceCalculator
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.domain.service.InventoryMovementService
import com.miara.cuentame.core.domain.usecase.IngredientValidator
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

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
    fun provideWeightedAverageCostCalculator(): WeightedAverageCostCalculator = WeightedAverageCostCalculator()

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
    fun provideIngredientValidator(): IngredientValidator = IngredientValidator()
}
