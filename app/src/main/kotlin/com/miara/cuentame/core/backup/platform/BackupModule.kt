package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BackupModule {

    @Binds
    @Singleton
    fun bindSnapshotSource(impl: RoomBackupSnapshotSource): BackupSnapshotSource

    @Binds
    @Singleton
    fun bindPreferencesSource(impl: DataStoreBackupPreferencesSource): BackupPreferencesSource

    @Binds
    @Singleton
    fun bindAttachmentSource(impl: AndroidBackupAttachmentSource): BackupAttachmentSource

    @Binds
    @Singleton
    fun bindDocumentStore(impl: AndroidBackupDocumentStore): BackupDocumentStore

    @Binds
    @Singleton
    fun bindErrorClassifier(impl: DefaultBackupStorageErrorClassifier): BackupStorageErrorClassifier

    @Binds
    @Singleton
    fun bindArchiveWriter(impl: DefaultBackupArchiveWriter): BackupArchiveWriter

    @Binds
    @Singleton
    fun bindArchiveValidator(impl: DefaultBackupArchiveValidator): BackupArchiveValidator
}
