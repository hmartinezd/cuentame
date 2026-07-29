package com.miara.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.miara.cuentame.core.backup.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupDocumentStore {

    override suspend fun openForWrite(destination: BackupDocumentUri): OutputStream {
        return try {
            val uri = Uri.parse(destination.value)
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw BackupDocumentOpenException(BackupDocumentOperation.WRITE)
            ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: BackupDocumentOpenException) {
            throw e
        } catch (e: Exception) {
            throw BackupDocumentOpenException(BackupDocumentOperation.WRITE, e)
        }
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        return try {
            val uri = Uri.parse(source.value)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw BackupDocumentOpenException(BackupDocumentOperation.READ)
            ParcelFileDescriptor.AutoCloseInputStream(pfd)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: BackupDocumentOpenException) {
            throw e
        } catch (e: Exception) {
            throw BackupDocumentOpenException(BackupDocumentOperation.READ, e)
        }
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
