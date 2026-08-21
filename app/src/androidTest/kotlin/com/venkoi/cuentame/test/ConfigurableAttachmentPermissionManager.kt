package com.venkoi.cuentame.test

import android.net.Uri
import com.venkoi.cuentame.core.common.attachment.LocalAttachmentPermissionManager
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
