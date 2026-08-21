package com.venkoi.restaurantops.core.di

import com.venkoi.restaurantops.core.common.AndroidAppVersionProvider
import com.venkoi.restaurantops.core.common.AppVersionProvider
import com.venkoi.restaurantops.core.domain.repository.BackupRepository
import com.venkoi.restaurantops.core.backup.AndroidBackupRepository
import com.venkoi.restaurantops.core.backup.ChecksumProvider
import com.venkoi.restaurantops.core.backup.Sha256ChecksumProvider
import com.venkoi.restaurantops.core.backup.api.PurchaseDocumentStore
import com.venkoi.restaurantops.core.backup.platform.AndroidPurchaseDocumentStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindAppVersionProvider(
        impl: AndroidAppVersionProvider
    ): AppVersionProvider

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        impl: AndroidBackupRepository
    ): BackupRepository

    @Binds
    @Singleton
    abstract fun bindChecksumProvider(
        impl: Sha256ChecksumProvider
    ): ChecksumProvider

    @Binds
    @Singleton
    abstract fun bindPurchaseDocumentStore(
        impl: AndroidPurchaseDocumentStore
    ): PurchaseDocumentStore
}
