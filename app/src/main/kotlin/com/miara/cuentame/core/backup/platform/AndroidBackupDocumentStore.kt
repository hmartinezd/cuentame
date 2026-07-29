package com.miara.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.miara.cuentame.core.backup.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
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
            ?: throw BackupDocumentOpenException(BackupDocumentOperation.WRITE)
        
        return ParcelFileDescriptor.AutoCloseOutputStream(pfd)
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        val uri = Uri.parse(source.value)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw BackupDocumentOpenException(BackupDocumentOperation.READ)
        
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
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
