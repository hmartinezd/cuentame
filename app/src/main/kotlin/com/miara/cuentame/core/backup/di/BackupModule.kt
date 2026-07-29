package com.miara.cuentame.core.backup.di

import com.miara.cuentame.core.backup.api.BackupAttachmentSource
import com.miara.cuentame.core.backup.api.BackupDocumentStore
import com.miara.cuentame.core.backup.api.BackupPreferencesSource
import com.miara.cuentame.core.backup.api.BackupSnapshotSource
import com.miara.cuentame.core.backup.platform.*
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

    @Binds
    @Singleton
    abstract fun bindBackupSnapshotSource(
        impl: RoomBackupSnapshotSource
    ): BackupSnapshotSource

    @Binds
    @Singleton
    abstract fun bindBackupPreferencesSource(
        impl: DataStoreBackupPreferencesSource
    ): BackupPreferencesSource

    companion object {
        @Provides
        @Singleton
        fun provideBackupStorageErrorClassifier(): BackupStorageErrorClassifier {
            return DefaultBackupStorageErrorClassifier()
        }
    }
}
