package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.ArchiveEntryValidator
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupArchiveProcessor @Inject constructor(
    private val readLimits: BackupReadLimits,
    private val zipFactory: BackupZipInputFactory
) {
    interface Sink {
        suspend fun onCoreEntry(name: String, bytes: ByteArray)
        suspend fun onAttachment(name: String, inputStream: InputStream, expectedSize: Long)
        fun shouldProcessAttachment(name: String): Boolean = true
    }

    suspend fun process(
        input: InputStream,
        sink: Sink
    ): BackupArchiveProcessingResult = withContext(Dispatchers.IO) {
        val zis = zipFactory.create(NonClosingInputStream(input))
        
        var primaryFailure: BackupRestoreFailure? = null
        var totalUncompressedBytes = 0L
        var entryCount = 0
        
        val calculatedChecksums = mutableMapOf<String, String>()
        val calculatedSizes = mutableMapOf<String, Long>()

        try {
            while (true) {
                val entry = try {
                    zis.nextEntry ?: break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    primaryFailure = BackupRestoreFailure.InvalidZip
                    break
                }

                entryCount++
                if (entryCount > readLimits.maxEntryCount) {
                    primaryFailure = BackupRestoreFailure.EntryLimitExceeded
                    break
                }

                val entryName = entry.name
                if (entry.isDirectory) {
                    primaryFailure = BackupRestoreFailure.UnexpectedEntry
                    break
                }

                if (!ArchiveEntryValidator.isSafe(entryName)) {
                    primaryFailure = BackupRestoreFailure.UnsafeEntryPath
                    break
                }

                if (calculatedChecksums.containsKey(entryName)) {
                    primaryFailure = BackupRestoreFailure.DuplicateEntry
                    break
                }

                val isCore = BackupFormatV1Contract.CORE_ENTRIES.contains(entryName)
                val entryLimit = when (entryName) {
                    BackupFormatV1Contract.DATABASE_ENTRY -> readLimits.maxDatabaseJsonBytes
                    BackupFormatV1Contract.PREFERENCES_ENTRY -> readLimits.maxSettingsJsonBytes
                    BackupFormatV1Contract.MANIFEST_ENTRY -> readLimits.maxManifestJsonBytes
                    BackupFormatV1Contract.CHECKSUMS_ENTRY -> readLimits.maxChecksumsJsonBytes
                    else -> {
                        if (!entryName.startsWith("attachments/")) {
                            primaryFailure = BackupRestoreFailure.UnexpectedEntry
                            -1L
                        } else {
                            readLimits.maxAttachmentBytes
                        }
                    }
                }
                if (primaryFailure != null) break

                val digest = MessageDigest.getInstance("SHA-256")
                var entrySize = 0L
                
                if (isCore) {
                    val entryContent = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zis.read(buffer)
                        if (n == -1) break
                        
                        entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                        totalUncompressedBytes = BackupByteMath.addExact(totalUncompressedBytes, n.toLong())
                        
                        if (entrySize > entryLimit || totalUncompressedBytes > readLimits.maxTotalUncompressedBytes) {
                            primaryFailure = if (entrySize > entryLimit) BackupRestoreFailure.EntryLimitExceeded else BackupRestoreFailure.TotalLimitExceeded
                            break
                        }
                        digest.update(buffer, 0, n)
                        entryContent.write(buffer, 0, n)
                    }
                    if (primaryFailure != null) break
                    sink.onCoreEntry(entryName, entryContent.toByteArray())
                } else if (sink.shouldProcessAttachment(entryName)) {
                    val streamingWrapper = object : InputStream() {
                        override fun read(): Int = throw UnsupportedOperationException()
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = zis.read(b, off, len)
                            if (n != -1) {
                                entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                                totalUncompressedBytes = BackupByteMath.addExact(totalUncompressedBytes, n.toLong())
                                if (entrySize > entryLimit || totalUncompressedBytes > readLimits.maxTotalUncompressedBytes) {
                                    primaryFailure = if (entrySize > entryLimit) BackupRestoreFailure.EntryLimitExceeded else BackupRestoreFailure.TotalLimitExceeded
                                    return -1
                                }
                                digest.update(b, off, n)
                            }
                            return n
                        }
                    }
                    sink.onAttachment(entryName, streamingWrapper, entry.size)
                } else {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zis.read(buffer)
                        if (n == -1) break
                        entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                        totalUncompressedBytes = BackupByteMath.addExact(totalUncompressedBytes, n.toLong())
                        if (entrySize > entryLimit || totalUncompressedBytes > readLimits.maxTotalUncompressedBytes) {
                            primaryFailure = if (entrySize > entryLimit) BackupRestoreFailure.EntryLimitExceeded else BackupRestoreFailure.TotalLimitExceeded
                            break
                        }
                        digest.update(buffer, 0, n)
                    }
                }
                
                if (primaryFailure != null) break
                
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                calculatedChecksums[entryName] = checksum
                calculatedSizes[entryName] = entrySize
                
                try {
                    zis.closeEntry()
                } catch (e: Exception) {
                    primaryFailure = BackupRestoreFailure.InvalidZip
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (primaryFailure == null) primaryFailure = BackupRestoreFailure.GenericIo
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                if (primaryFailure == null) primaryFailure = BackupRestoreFailure.GenericIo
            }
        }

        primaryFailure?.let { return@withContext BackupArchiveProcessingResult.Failure(it) }
        
        BackupArchiveProcessingResult.Success(calculatedChecksums, calculatedSizes)
    }

    private class NonClosingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        override fun close() {}
    }
}

sealed interface BackupArchiveProcessingResult {
    data class Success(
        val checksums: Map<String, String>,
        val sizes: Map<String, Long>
    ) : BackupArchiveProcessingResult
    data class Failure(val reason: BackupRestoreFailure) : BackupArchiveProcessingResult
}
