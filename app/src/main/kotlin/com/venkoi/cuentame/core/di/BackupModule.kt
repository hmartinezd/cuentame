package com.venkoi.cuentame.core.di

import com.venkoi.cuentame.core.common.AndroidAppVersionProvider
import com.venkoi.cuentame.core.common.AppVersionProvider
import com.venkoi.cuentame.core.domain.repository.BackupRepository
import com.venkoi.cuentame.core.backup.AndroidBackupRepository
import com.venkoi.cuentame.core.backup.ChecksumProvider
import com.venkoi.cuentame.core.backup.Sha256ChecksumProvider
import com.venkoi.cuentame.core.backup.api.PurchaseDocumentStore
import com.venkoi.cuentame.core.backup.platform.AndroidPurchaseDocumentStore
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
