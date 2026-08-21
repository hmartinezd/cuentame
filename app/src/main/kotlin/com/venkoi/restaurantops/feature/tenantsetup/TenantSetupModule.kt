package com.venkoi.restaurantops.feature.tenantsetup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TenantSetupModule {
    @Binds
    abstract fun bindDefaultsProvider(
        implementation: AndroidTenantSetupDefaultsProvider
    ): TenantSetupDefaultsProvider
}
