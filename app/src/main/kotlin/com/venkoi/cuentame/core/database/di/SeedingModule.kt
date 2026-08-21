package com.venkoi.cuentame.core.database.di

import com.venkoi.cuentame.core.database.seed.RoomStarterCatalogSeeder
import com.venkoi.cuentame.core.domain.service.StarterCatalogSeeder

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedingModule {

    @Binds
    @Singleton
    abstract fun bindStarterCatalogSeeder(seeder: RoomStarterCatalogSeeder): StarterCatalogSeeder
}
