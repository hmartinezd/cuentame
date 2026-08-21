package com.venkoi.restaurantops.core.backup.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RestoreFailureModule {
    @Binds
    @Singleton
    fun bindRestoreFailureInjector(impl: NoOpRestoreFailureInjector): RestoreFailureInjector
}
