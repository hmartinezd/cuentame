package com.venkoi.cuentame.core.common.attachment

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface LocalAttachmentPermissionManager {
    fun persistReadPermission(uri: Uri): Result<Unit>
}

@Singleton
class AndroidLocalAttachmentPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalAttachmentPermissionManager {
    override fun persistReadPermission(uri: Uri): Result<Unit> {
        return try {
            val contentResolver = context.contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
