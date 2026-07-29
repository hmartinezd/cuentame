package com.miara.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.miara.cuentame.core.backup.api.BackupDocumentStore
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupDocumentStore {

    override suspend fun openForWrite(destination: BackupDocumentUri): OutputStream {
        val uri = Uri.parse(destination.value)
        val pfd = context.contentResolver.openFileDescriptor(uri, "w")
            ?: throw IllegalStateException("Could not open file descriptor for write")
        return FileOutputStream(pfd.fileDescriptor)
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        val uri = Uri.parse(source.value)
        return context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open input stream for read")
    }

    override suspend fun delete(document: BackupDocumentUri): Boolean {
        val uri = Uri.parse(document.value)
        val resolver = context.contentResolver

        val deletedByDocumentsContract = runCatching {
            DocumentsContract.deleteDocument(
                resolver,
                uri
            )
        }.getOrDefault(false)

        if (deletedByDocumentsContract) {
            return true
        }

        return runCatching {
            resolver.delete(
                uri,
                null,
                null
            ) > 0
        }.getOrDefault(false)
    }

    override suspend fun truncate(document: BackupDocumentUri): Boolean {
        val uri = Uri.parse(document.value)
        return try {
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
