package com.miara.cuentame.core.backup.di

import com.miara.cuentame.core.backup.api.BackupAttachmentSource
import com.miara.cuentame.core.backup.api.BackupDocumentStore
import com.miara.cuentame.core.backup.platform.AndroidBackupAttachmentSource
import com.miara.cuentame.core.backup.platform.AndroidBackupDocumentStore
import com.miara.cuentame.core.backup.platform.BackupStorageErrorClassifier
import com.miara.cuentame.core.backup.platform.DefaultBackupStorageErrorClassifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupDocumentStore(
        impl: AndroidBackupDocumentStore
    ): BackupDocumentStore

    @Binds
    @Singleton
    abstract fun bindBackupAttachmentSource(
        impl: AndroidBackupAttachmentSource
    ): BackupAttachmentSource

    companion object {
        @Provides
        @Singleton
        fun provideBackupStorageErrorClassifier(): BackupStorageErrorClassifier {
            return DefaultBackupStorageErrorClassifier()
        }
    }
}
