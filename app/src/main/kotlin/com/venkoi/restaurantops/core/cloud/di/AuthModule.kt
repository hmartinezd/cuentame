package com.venkoi.restaurantops.core.cloud.di

import com.venkoi.restaurantops.core.cloud.auth.SupabaseAuthRepository
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        implementation: SupabaseAuthRepository
    ): AuthRepository
}
