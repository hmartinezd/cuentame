package com.miara.cuentame.core.backup.api

@JvmInline
value class BackupDocumentUri(val value: String)

interface BackupDocumentStore {
    suspend fun openForWrite(destination: BackupDocumentUri): java.io.OutputStream
    suspend fun openForRead(source: BackupDocumentUri): java.io.InputStream
    suspend fun delete(document: BackupDocumentUri): Boolean
    suspend fun truncate(document: BackupDocumentUri): Boolean
}
