package com.venkoi.restaurantops.core.cloud.di

import com.venkoi.restaurantops.BuildConfig
import com.venkoi.restaurantops.core.cloud.sync.SupabaseInventoryAreaSyncRemoteDataSource
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    @Provides
    @Singleton
    fun provideInventoryAreaSyncRemoteDataSource(
        implementation: SupabaseInventoryAreaSyncRemoteDataSource
    ): InventoryAreaSyncRemoteDataSource = implementation
}
