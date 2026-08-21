package com.venkoi.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.venkoi.cuentame.core.backup.api.AttachmentSourceMetadata
import com.venkoi.cuentame.core.backup.api.AttachmentSourceUri
import com.venkoi.cuentame.core.backup.api.BackupAttachmentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupAttachmentSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupAttachmentSource {

    override suspend fun inspect(uri: AttachmentSourceUri): AttachmentSourceMetadata {
        val parsedUri = Uri.parse(uri.value)
        if (parsedUri.scheme == "content") {
            val displayName = context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
            } ?: parsedUri.lastPathSegment

            val mimeType = context.contentResolver.getType(parsedUri)

            return AttachmentSourceMetadata(uri, displayName, mimeType)
        } else {
            val file = if (parsedUri.scheme == "file") {
                File(parsedUri.path ?: throw IllegalArgumentException("Invalid file URI"))
            } else {
                File(context.filesDir, uri.value)
            }
            
            if (!file.exists()) {
                throw java.io.FileNotFoundException("Attachment file not found: ${file.absolutePath}")
            }

            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

            return AttachmentSourceMetadata(uri, file.name, mimeType)
        }
    }

    override suspend fun open(uri: AttachmentSourceUri): InputStream {
        val parsedUri = Uri.parse(uri.value)
        if (parsedUri.scheme == null || parsedUri.scheme == "file") {
            val file = if (parsedUri.scheme == "file") {
                File(parsedUri.path ?: throw IllegalArgumentException("Invalid file URI"))
            } else {
                File(context.filesDir, uri.value)
            }
            return file.inputStream()
        }
        return context.contentResolver.openInputStream(parsedUri)
            ?: throw IllegalStateException("Could not open attachment stream")
    }
}
