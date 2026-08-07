package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.internal.RestoreFailureInjector
import com.miara.cuentame.core.backup.internal.RestoreFailureModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RestoreFailureModule::class]
)
object TestRestoreFailureModule {
    @Provides
    @Singleton
    fun provideFailureInjector(): AttachmentBackupRestoreIntegrationTest.TestFailureInjector = 
        AttachmentBackupRestoreIntegrationTest.TestFailureInjector()

    @Provides
    @Singleton
    fun bindFailureInjector(impl: AttachmentBackupRestoreIntegrationTest.TestFailureInjector): RestoreFailureInjector = impl
}
