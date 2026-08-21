package com.venkoi.restaurantops.core.cloud.di

import com.venkoi.restaurantops.core.cloud.device.SupabaseDeviceInstallationRepository
import com.venkoi.restaurantops.core.device.AndroidInstallationIdProvider
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRepository
import com.venkoi.restaurantops.core.domain.service.InstallationIdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceInstallationModule {

    @Binds
    @Singleton
    abstract fun bindInstallationIdProvider(
        implementation: AndroidInstallationIdProvider
    ): InstallationIdProvider

    @Binds
    @Singleton
    abstract fun bindDeviceInstallationRepository(
        implementation: SupabaseDeviceInstallationRepository
    ): DeviceInstallationRepository
}
