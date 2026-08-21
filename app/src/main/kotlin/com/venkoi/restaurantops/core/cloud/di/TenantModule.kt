package com.venkoi.restaurantops.core.cloud.di

import com.venkoi.restaurantops.core.cloud.tenant.SupabaseTenantRepository
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TenantModule {

    @Binds
    @Singleton
    abstract fun bindTenantRepository(
        implementation: SupabaseTenantRepository
    ): TenantRepository
}
