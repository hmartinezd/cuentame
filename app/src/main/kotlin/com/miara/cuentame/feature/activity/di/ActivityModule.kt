package com.miara.cuentame.feature.activity.di

import com.miara.cuentame.feature.activity.logic.AndroidInventoryActivityTextResolver
import com.miara.cuentame.feature.activity.logic.InventoryActivityTextResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityModule {

    @Binds
    @Singleton
    abstract fun bindInventoryActivityTextResolver(
        impl: AndroidInventoryActivityTextResolver
    ): InventoryActivityTextResolver
}
