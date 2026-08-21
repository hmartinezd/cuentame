package com.venkoi.cuentame.core.backup.api

import java.io.InputStream

@JvmInline
value class AttachmentSourceUri(val value: String)

data class AttachmentSourceMetadata(
    val uri: AttachmentSourceUri,
    val displayName: String?,
    val mimeType: String?
)

interface BackupAttachmentSource {
    suspend fun inspect(uri: AttachmentSourceUri): AttachmentSourceMetadata
    suspend fun open(uri: AttachmentSourceUri): InputStream
}
