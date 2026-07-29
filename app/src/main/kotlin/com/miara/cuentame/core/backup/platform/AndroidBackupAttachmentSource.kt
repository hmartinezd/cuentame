package com.miara.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miara.cuentame.core.backup.api.AttachmentSourceMetadata
import com.miara.cuentame.core.backup.api.AttachmentSourceUri
import com.miara.cuentame.core.backup.api.BackupAttachmentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupAttachmentSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupAttachmentSource {

    override suspend fun inspect(uri: AttachmentSourceUri): AttachmentSourceMetadata {
        val parsedUri = Uri.parse(uri.value)
        val displayName = if (parsedUri.scheme == "content") {
            context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
            } ?: parsedUri.lastPathSegment
        } else {
            parsedUri.lastPathSegment
        }

        val mimeType = if (parsedUri.scheme == "content") {
            context.contentResolver.getType(parsedUri)
        } else null

        return AttachmentSourceMetadata(uri, displayName, mimeType)
    }

    override suspend fun open(uri: AttachmentSourceUri): InputStream {
        val parsedUri = Uri.parse(uri.value)
        return context.contentResolver.openInputStream(parsedUri)
            ?: throw IllegalStateException("Could not open attachment stream")
    }
}
