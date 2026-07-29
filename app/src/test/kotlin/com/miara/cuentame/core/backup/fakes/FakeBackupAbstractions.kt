package com.miara.cuentame.core.backup.fakes

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class FakeBackupDocumentStore : BackupDocumentStore {
    val openForWriteCalls = mutableListOf<BackupDocumentUri>()
    val openForReadCalls = mutableListOf<BackupDocumentUri>()
    val deleteCalls = mutableListOf<BackupDocumentUri>()
    val truncateCalls = mutableListOf<BackupDocumentUri>()

    var deleteResult = true
    var truncateResult = true
    var writeStream = ByteArrayOutputStream()
    var readStream: InputStream = ByteArrayInputStream(ByteArray(0))

    override suspend fun openForWrite(destination: BackupDocumentUri): OutputStream {
        openForWriteCalls.add(destination)
        return writeStream
    }

    override suspend fun openForRead(source: BackupDocumentUri): InputStream {
        openForReadCalls.add(source)
        return readStream
    }

    override suspend fun delete(document: BackupDocumentUri): Boolean {
        deleteCalls.add(document)
        return deleteResult
    }

    override suspend fun truncate(document: BackupDocumentUri): Boolean {
        truncateCalls.add(document)
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
    var exception: Exception? = null

    override suspend fun inspect(uri: AttachmentSourceUri): AttachmentSourceMetadata {
        inspectedUris.add(uri)
        return metadataMap[uri] ?: throw Exception("Not found")
    }

    override suspend fun open(uri: AttachmentSourceUri): InputStream {
        openedUris.add(uri)
        exception?.let { throw it }
        val data = dataMap[uri] ?: throw Exception("Not found")
        return ByteArrayInputStream(data)
    }
}
