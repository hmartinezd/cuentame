package com.miara.cuentame.core.backup.fakes

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

open class FakeBackupDocumentStore : BackupDocumentStore {
    val openForWriteCalls = mutableListOf<BackupDocumentUri>()
    val openForReadCalls = mutableListOf<BackupDocumentUri>()
    val deleteCalls = mutableListOf<BackupDocumentUri>()
    val truncateCalls = mutableListOf<BackupDocumentUri>()

    var deleteResult = true
    var truncateResult = true
    var storage = mutableMapOf<BackupDocumentUri, ByteArray>()
    
    var openForWriteError: Exception? = null
    var openForReadError: Exception? = null

    override suspend fun openForWrite(destination: BackupDocumentUri): OutputStream {
        openForWriteCalls.add(destination)
        openForWriteError?.let { throw it }
        return object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                storage[destination] = toByteArray()
            }
        }
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        openForReadCalls.add(source)
        openForReadError?.let { throw it }
        val data = storage[source] ?: throw IOException("Not found")
        return ByteArrayInputStream(data)
    }

    override suspend fun delete(document: BackupDocumentUri): Boolean {
        deleteCalls.add(document)
        if (deleteResult) storage.remove(document)
        return deleteResult
    }

    override suspend fun truncate(document: BackupDocumentUri): Boolean {
        truncateCalls.add(document)
        if (truncateResult) storage[document] = ByteArray(0)
        return truncateResult
    }
}

class FakeBackupSnapshotSource : BackupSnapshotSource {
    var result: BackupSnapshotResult? = null
    var exception: Exception? = null

    override suspend fun loadSnapshot(restaurantId: String): BackupSnapshotResult {
        exception?.let { throw it }
        return result ?: throw IllegalStateException("Result not set")
    }
}

class FakeBackupPreferencesSource : BackupPreferencesSource {
    var result: BackupPreferencesDto? = null
    var exception: Exception? = null

    override suspend fun loadPreferences(): BackupPreferencesDto {
        exception?.let { throw it }
        return result ?: throw IllegalStateException("Result not set")
    }
}

class FakeBackupAttachmentSource : BackupAttachmentSource {
    val inspectedUris = mutableListOf<AttachmentSourceUri>()
    val openedUris = mutableListOf<AttachmentSourceUri>()
    
    var metadataMap = mutableMapOf<AttachmentSourceUri, AttachmentSourceMetadata>()
    var dataMap = mutableMapOf<AttachmentSourceUri, ByteArray>()
    var inspectException: Exception? = null
    var openException: Exception? = null
    
    var openCountMap = mutableMapOf<AttachmentSourceUri, Int>()

    override suspend fun inspect(uri: AttachmentSourceUri): AttachmentSourceMetadata {
        inspectedUris.add(uri)
        inspectException?.let { throw it }
        return metadataMap[uri] ?: throw Exception("Not found")
    }

    override suspend fun open(uri: AttachmentSourceUri): InputStream {
        openedUris.add(uri)
        openException?.let { throw it }
        openCountMap[uri] = (openCountMap[uri] ?: 0) + 1
        val data = dataMap[uri] ?: throw Exception("Not found")
        return ByteArrayInputStream(data)
    }
}
