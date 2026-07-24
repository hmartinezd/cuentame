package com.miara.cuentame.core.di

import com.miara.cuentame.core.database.repository.IntegrationFailureBoundary
import com.miara.cuentame.core.database.repository.NoOpFailureBoundary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IntegrationModule {
    @Provides
    @Singleton
    fun provideIntegrationFailureBoundary(): IntegrationFailureBoundary = NoOpFailureBoundary()
}
