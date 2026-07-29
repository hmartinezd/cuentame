package com.miara.cuentame.core.backup.api

import java.io.InputStream
import java.io.OutputStream

@JvmInline
value class BackupDocumentUri(val value: String)

enum class BackupDocumentOperation {
    READ,
    WRITE
}

class BackupDocumentOpenException(
    val operation: BackupDocumentOperation,
    override val cause: Throwable? = null
) : Exception("Failed to open backup document for $operation")

interface BackupDocumentStore {
    suspend fun openForWrite(destination: BackupDocumentUri): OutputStream
    suspend fun openForRead(source: BackupDocumentUri): InputStream
    suspend fun delete(document: BackupDocumentUri): Boolean
    suspend fun truncate(document: BackupDocumentUri): Boolean
}
