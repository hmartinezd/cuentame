package com.miara.cuentame.core.backup.api

import java.io.IOException
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
    cause: Throwable? = null
) : IOException(cause)

interface BackupDocumentStore {
    suspend fun openForWrite(destination: BackupDocumentUri): OutputStream
    suspend fun openForRead(source: BackupDocumentUri): InputStream
    suspend fun delete(document: BackupDocumentUri): Boolean
    suspend fun truncate(document: BackupDocumentUri): Boolean
}
