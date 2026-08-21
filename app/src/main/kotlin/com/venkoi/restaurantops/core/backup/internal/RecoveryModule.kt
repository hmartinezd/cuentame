package com.venkoi.restaurantops.core.backup.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RecoveryModule {
    @Binds
    @Singleton
    fun bindRecoveryBootstrapper(impl: DefaultRecoveryBootstrapper): RecoveryBootstrapper
}
