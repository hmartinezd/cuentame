package com.venkoi.restaurantops.core.di

import android.content.Context
import com.venkoi.restaurantops.core.common.attachment.AndroidLocalAttachmentPermissionManager
import com.venkoi.restaurantops.core.common.attachment.LocalAttachmentPermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalAttachmentModule {
    @Provides
    @Singleton
    fun provideLocalAttachmentPermissionManager(
        @ApplicationContext context: Context
    ): LocalAttachmentPermissionManager = AndroidLocalAttachmentPermissionManager(context)
}
