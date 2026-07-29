package com.miara.cuentame.core.di

import com.miara.cuentame.core.common.AndroidAppVersionProvider
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.backup.AndroidBackupRepository
import com.miara.cuentame.core.backup.ChecksumProvider
import com.miara.cuentame.core.backup.Sha256ChecksumProvider
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
}
