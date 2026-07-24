package com.miara.cuentame.core.di

import com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
import com.miara.cuentame.core.database.repository.IntegrationFailureBoundary
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [IntegrationModule::class]
)
object TestIntegrationModule {
    @Provides
    @Singleton
    fun provideConfigurableFailureBoundary(): ConfigurableFailureBoundary = ConfigurableFailureBoundary()

    @Provides
    @Singleton
    fun provideIntegrationFailureBoundary(configurable: ConfigurableFailureBoundary): IntegrationFailureBoundary = configurable
}
