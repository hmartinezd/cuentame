package com.miara.cuentame.core.di

import android.net.Uri
import com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigurableAttachmentPermissionManager @Inject constructor() : LocalAttachmentPermissionManager {
    var shouldFail = false
    override fun persistReadPermission(uri: Uri): Result<Unit> {
        return if (shouldFail) {
            Result.failure(RuntimeException("Permission failed"))
        } else {
            Result.success(Unit)
        }
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LocalAttachmentModule::class]
)
object TestLocalAttachmentModule {
    @Provides
    @Singleton
    fun provideLocalAttachmentPermissionManager(): LocalAttachmentPermissionManager = ConfigurableAttachmentPermissionManager()
}
