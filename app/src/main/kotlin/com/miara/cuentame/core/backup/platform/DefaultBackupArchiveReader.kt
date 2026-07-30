package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.ArchiveEntryValidator
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityException
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityValidator
import com.miara.cuentame.core.backup.ChecksumParser
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBackupArchiveReader @Inject constructor(
    private val codecs: BackupJsonCodecs,
    private val readLimits: BackupReadLimits = BackupReadLimits()
) : BackupArchiveReader {

    override suspend fun inspect(
        input: InputStream,
        source: BackupDocumentUri
    ): BackupArchiveInspectionResult = withContext(Dispatchers.IO) {
        // ZipInputStream(NonClosingInputStream(input)) ensures we don't close the caller's stream
        val zis = ZipInputStream(NonClosingInputStream(input))
        
        var totalUncompressedBytes = 0L
        var entryCount = 0
        
        val calculatedChecksums = mutableMapOf<String, String>()
        val calculatedSizes = mutableMapOf<String, Long>()
        
        var databaseJsonBytes: ByteArray? = null
        var preferencesJsonBytes: ByteArray? = null
        var manifestJsonBytes: ByteArray? = null
        var checksumsJsonBytes: ByteArray? = null

        try {
            while (true) {
                val entry = try {
                    zis.nextEntry ?: break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
                }

                entryCount++
                if (entryCount > readLimits.maxEntryCount) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                }

                val entryName = entry.name
                if (!ArchiveEntryValidator.isSafe(entryName)) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.UnsafeEntryPath)
                }
                
                if (calculatedChecksums.containsKey(entryName)) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.DuplicateEntry)
                }

                if (entry.isDirectory) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.UnexpectedEntry)
                }

                val entryLimit = when (entryName) {
                    BackupFormatV1Contract.DATABASE_ENTRY -> readLimits.maxDatabaseJsonBytes
                    BackupFormatV1Contract.PREFERENCES_ENTRY -> readLimits.maxSettingsJsonBytes
                    BackupFormatV1Contract.MANIFEST_ENTRY -> readLimits.maxManifestJsonBytes
                    BackupFormatV1Contract.CHECKSUMS_ENTRY -> readLimits.maxChecksumsJsonBytes
                    else -> {
                        if (!entryName.startsWith("attachments/")) {
                            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.UnexpectedEntry)
                        }
                        readLimits.maxAttachmentBytes
                    }
                }

                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var entrySize = 0L
                val entryContent = if (BackupFormatV1Contract.CORE_ENTRIES.contains(entryName)) {
                    java.io.ByteArrayOutputStream()
                } else null

                try {
                    while (true) {
                        val n = try {
                            zis.read(buffer)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
                        }
                        if (n == -1) break
                        
                        entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                        totalUncompressedBytes = BackupByteMath.addExact(totalUncompressedBytes, n.toLong())
                        
                        if (entrySize > entryLimit) {
                            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                        }
                        if (totalUncompressedBytes > readLimits.maxTotalUncompressedBytes) {
                            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
                        }
                        
                        digest.update(buffer, 0, n)
                        entryContent?.write(buffer, 0, n)
                    }
                } catch (e: BackupSizeOverflowException) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
                }
                
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                calculatedChecksums[entryName] = checksum
                calculatedSizes[entryName] = entrySize
                
                when (entryName) {
                    BackupFormatV1Contract.DATABASE_ENTRY -> databaseJsonBytes = entryContent!!.toByteArray()
                    BackupFormatV1Contract.PREFERENCES_ENTRY -> preferencesJsonBytes = entryContent!!.toByteArray()
                    BackupFormatV1Contract.MANIFEST_ENTRY -> manifestJsonBytes = entryContent!!.toByteArray()
                    BackupFormatV1Contract.CHECKSUMS_ENTRY -> checksumsJsonBytes = entryContent!!.toByteArray()
                }
                
                try {
                    zis.closeEntry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.GenericIo)
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                // Ignore failure during close of ZipInputStream (wrapper)
            }
        }

        // 1. Core entries existence
        if (databaseJsonBytes == null || preferencesJsonBytes == null || manifestJsonBytes == null || checksumsJsonBytes == null) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MissingCoreEntry)
        }

        // 2. Checksums.json validation
        val declaredChecksums = try {
            val jsonStr = checksumsJsonBytes.decodeToString()
            ChecksumParser.parse(jsonStr).getOrElse {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
            }
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
        }
        
        // checksums.json must contain all payload entries (excluding itself)
        val payloadEntries = calculatedChecksums.keys - BackupFormatV1Contract.CHECKSUMS_ENTRY
        if (declaredChecksums.keys != payloadEntries) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
        }
        
        for (entryName in payloadEntries) {
            if (!declaredChecksums[entryName].equals(calculatedChecksums[entryName], ignoreCase = true)) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
            }
            if (!BackupFormatV1Contract.isValidChecksum(declaredChecksums[entryName]!!)) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
            }
        }

        // 3. Manifest parsing and structural validation
        val manifest = try {
            codecs.reader.decodeFromString<BackupManifest>(manifestJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
        }

        BackupManifestContractValidator.validateManifestStructure(manifest, calculatedChecksums, calculatedSizes)?.let { failure ->
            return@withContext BackupArchiveInspectionResult.Failure(failure)
        }

        // 4. Snapshot parsing and logical integrity
        val snapshot = try {
            codecs.reader.decodeFromString<BackupSnapshotDto>(databaseJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedSnapshot)
        }

        BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)?.let { failure ->
            return@withContext BackupArchiveInspectionResult.Failure(failure)
        }

        BackupSnapshotIntegrityValidator.validate(snapshot, manifest).getOrElse { e ->
            val code = (e as? BackupSnapshotIntegrityException)?.code ?: BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.SnapshotIntegrityFailure(code))
        }

        // 5. Preferences parsing
        val preferences = try {
            codecs.reader.decodeFromString<BackupPreferencesDto>(preferencesJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        // 6. Success - Produce preview
        val totalRecordCount = try {
            manifest.tableMetadata
                .filter { it.key !in BackupFormatV1Contract.DERIVED_TABLES }
                .values.fold(0L) { acc, meta -> BackupByteMath.addExact(acc, meta.entryCount.toLong()) }
        } catch (e: BackupSizeOverflowException) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
        }

        val preview = BackupRestorePreview(
            restaurantName = manifest.restaurantName!!,
            createdAt = try { java.time.Instant.parse(manifest.createdAtUtc).toEpochMilli() } catch (e: Exception) { null },
            backupFormatVersion = manifest.backupFormatVersion,
            databaseSchemaVersion = manifest.databaseSchemaVersion,
            localeTag = manifest.localeTag!!,
            totalRecordCount = totalRecordCount,
            attachmentCount = manifest.attachments.size,
            totalAttachmentBytes = manifest.attachments.sumOf { it.sizeBytes }
        )

        BackupArchiveInspectionResult.Ready(
            archive = InspectedBackupArchive.create(
                snapshot = snapshot,
                preferences = preferences,
                manifest = manifest,
                attachmentSummaries = manifest.attachments.map {
                    InspectedBackupAttachment(
                        attachmentId = it.attachmentId,
                        archivePath = it.archivePath,
                        displayName = it.displayName,
                        sizeBytes = it.sizeBytes,
                        checksumSha256 = it.checksumSha256
                    )
                },
                source = source
            ),
            preview = preview
        )
    }

    private class NonClosingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        override fun close() {
            // Do nothing, stream is managed by repository
        }
    }
}
