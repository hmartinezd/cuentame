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

internal interface ParcelFileDescriptorStreamFactory {
    fun createInputStream(descriptor: ParcelFileDescriptor): InputStream
    fun createOutputStream(descriptor: ParcelFileDescriptor): OutputStream
}

private class DefaultParcelFileDescriptorStreamFactory : ParcelFileDescriptorStreamFactory {
    override fun createInputStream(descriptor: ParcelFileDescriptor): InputStream = 
        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    override fun createOutputStream(descriptor: ParcelFileDescriptor): OutputStream = 
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
}

@Singleton
open class AndroidBackupDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupDocumentStore {

    // Internal for testing
    internal var streamFactory: ParcelFileDescriptorStreamFactory = DefaultParcelFileDescriptorStreamFactory()

    override suspend fun openForWrite(destination: BackupDocumentUri): OutputStream {
        return openStream(destination, "w", BackupDocumentOperation.WRITE) { pfd ->
            streamFactory.createOutputStream(pfd)
        }
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        return openStream(source, "r", BackupDocumentOperation.READ) { pfd ->
            streamFactory.createInputStream(pfd)
        }
    }

    private inline fun <T> openStream(
        uriWrapper: BackupDocumentUri,
        mode: String,
        operation: BackupDocumentOperation,
        createStream: (ParcelFileDescriptor) -> T
    ): T {
        val uri = Uri.parse(uriWrapper.value)
        val pfd = try {
            openDescriptor(uri, mode)
                ?: throw BackupDocumentOpenException(operation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw BackupDocumentOpenException(operation, e)
        }

        try {
            return createStream(pfd)
        } catch (e: CancellationException) {
            closeSuppressing(pfd, e)
            throw e
        } catch (security: SecurityException) {
            closeSuppressing(pfd, security)
            throw security
        } catch (error: Exception) {
            closeSuppressing(pfd, error)
            throw BackupDocumentOpenException(operation, error)
        }
    }

    internal open fun openDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? {
        return context.contentResolver.openFileDescriptor(uri, mode)
    }

    internal fun closeSuppressing(descriptor: ParcelFileDescriptor, primary: Throwable) {
        try {
            descriptor.close()
        } catch (closeError: Throwable) {
            if (
                closeError is VirtualMachineError ||
                closeError is ThreadDeath ||
                closeError is LinkageError
            ) {
                throw closeError
            }

            primary.addSuppressed(closeError)
        }
    }

    override suspend fun delete(document: BackupDocumentUri): Boolean {
        val uri = Uri.parse(document.value)
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = java.io.File(path)
            return if (file.exists()) file.delete() else true
        }

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
        return try {
            val uri = Uri.parse(document.value)
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
