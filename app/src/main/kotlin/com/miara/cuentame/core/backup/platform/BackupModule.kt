package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RoomRestoreDatabaseApplier
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    @Binds
    @Singleton
    fun bindArchiveReader(impl: DefaultBackupArchiveReader): BackupArchiveReader

    @Binds
    @Singleton
    fun bindRestoreRepository(impl: AndroidBackupRestoreRepository): BackupRestoreRepository

    @Binds
    @Singleton
    fun bindRestoreDatabaseApplier(impl: RoomRestoreDatabaseApplier): com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier

    @Binds
    @Singleton
    fun bindBackupRestoreCoordinator(impl: BackupRestoreCoordinatorImpl): BackupRestoreCoordinator

    @Binds
    @Singleton
    fun bindPurchaseInvoiceScanner(impl: GmsPurchaseInvoiceScanner): PurchaseInvoiceScanner

    companion object {
        @Provides
        @Singleton
        fun provideWriteLimits(): BackupWriteLimits = BackupWriteLimits()

        @Provides
        @Singleton
        fun provideReadLimits(): BackupReadLimits = BackupReadLimits()

        @Provides
        @Singleton
        fun provideZipInputFactory(): BackupZipInputFactory = BackupZipInputFactory { input -> java.util.zip.ZipInputStream(input) }
    }
}
