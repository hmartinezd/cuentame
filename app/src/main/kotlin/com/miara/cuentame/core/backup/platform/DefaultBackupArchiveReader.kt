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

                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var entrySize = 0L
                val entryContent = if (BackupFormatV1Contract.CORE_ENTRIES.contains(entryName)) {
                    java.io.ByteArrayOutputStream()
                } else null

                while (true) {
                    val n = try {
                        zis.read(buffer)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
                    }
                    if (n == -1) break
                    
                    digest.update(buffer, 0, n)
                    entryContent?.write(buffer, 0, n)
                    
                    entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                    totalUncompressedBytes = BackupByteMath.addExact(totalUncompressedBytes, n.toLong())
                    
                    if (totalUncompressedBytes > readLimits.maxTotalUncompressedBytes) {
                        return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
                    }
                    
                    if (entryContent == null && entrySize > readLimits.maxAttachmentBytes) {
                        return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                    }
                }
                
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                calculatedChecksums[entryName] = checksum
                calculatedSizes[entryName] = entrySize
                
                when (entryName) {
                    BackupFormatV1Contract.DATABASE_ENTRY -> {
                        if (entrySize > readLimits.maxDatabaseJsonBytes) return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                        databaseJsonBytes = entryContent!!.toByteArray()
                    }
                    BackupFormatV1Contract.PREFERENCES_ENTRY -> {
                        if (entrySize > readLimits.maxSettingsJsonBytes) return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                        preferencesJsonBytes = entryContent!!.toByteArray()
                    }
                    BackupFormatV1Contract.MANIFEST_ENTRY -> {
                        if (entrySize > readLimits.maxManifestJsonBytes) return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                        manifestJsonBytes = entryContent!!.toByteArray()
                    }
                    BackupFormatV1Contract.CHECKSUMS_ENTRY -> {
                        if (entrySize > readLimits.maxChecksumsJsonBytes) return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.EntryLimitExceeded)
                        checksumsJsonBytes = entryContent!!.toByteArray()
                    }
                    else -> {
                        if (!entryName.startsWith("attachments/")) {
                            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.UnexpectedEntry)
                        }
                    }
                }
                
                try {
                    zis.closeEntry()
                } catch (e: Exception) {
                    // Ignore close failure
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupSizeOverflowException) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.GenericIo)
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
        
        val payloadEntries = calculatedChecksums.keys - BackupFormatV1Contract.CHECKSUMS_ENTRY
        if (declaredChecksums.keys != payloadEntries) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
        }
        
        for (entryName in payloadEntries) {
            if (declaredChecksums[entryName] != calculatedChecksums[entryName]) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
            }
        }

        // 3. Manifest parsing and validation
        val manifest = try {
            codecs.reader.decodeFromString<BackupManifest>(manifestJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
        }

        if (manifest.backupFormatVersion != BackupFormatV1Contract.BACKUP_FORMAT_VERSION) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.UnsupportedFormatVersion)
        }
        if (manifest.databaseSchemaVersion != BackupFormatV1Contract.DATABASE_SCHEMA_VERSION) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.IncompatibleSchemaVersion)
        }
        if (!manifest.includedSections.containsAll(BackupFormatV1Contract.REQUIRED_SECTIONS)) {
             return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
        }

        // 4. ZIP vs Manifest cross-check
        val zipAttachments = calculatedChecksums.keys.filter { it.startsWith("attachments/") }.toSet()
        val manifestAttachments = manifest.attachments.map { it.archivePath }.toSet()
        
        if (zipAttachments != manifestAttachments) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ManifestMismatch)
        }
        
        val attachmentSummaries = mutableListOf<InspectedBackupAttachment>()
        for (mAtt in manifest.attachments) {
            if (!BackupFormatV1Contract.isValidAttachmentId(mAtt.attachmentId)) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
            }
            if (mAtt.archivePath != BackupFormatV1Contract.attachmentArchivePath(mAtt.attachmentId, mAtt.displayName)) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
            }
            
            val zipSize = calculatedSizes[mAtt.archivePath] ?: return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ManifestMismatch)
            val zipChecksum = calculatedChecksums[mAtt.archivePath] ?: return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ManifestMismatch)
            
            if (zipSize != mAtt.sizeBytes || zipChecksum != mAtt.checksumSha256) {
                return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.AttachmentMismatch)
            }
            
            attachmentSummaries.add(
                InspectedBackupAttachment(
                    attachmentId = mAtt.attachmentId,
                    archivePath = mAtt.archivePath,
                    displayName = mAtt.displayName,
                    sizeBytes = mAtt.sizeBytes,
                    checksumSha256 = mAtt.checksumSha256
                )
            )
        }

        // 5. Snapshot parsing and integrity validation
        val snapshot = try {
            codecs.reader.decodeFromString<BackupSnapshotDto>(databaseJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedSnapshot)
        }

        BackupSnapshotIntegrityValidator.validate(snapshot, manifest).getOrElse { e ->
            val code = (e as? BackupSnapshotIntegrityException)?.code ?: BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.SnapshotIntegrityFailure(code))
        }

        // 6. Preferences parsing
        val preferences = try {
            codecs.reader.decodeFromString<BackupPreferencesDto>(preferencesJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        // 7. Success - Produce preview
        val preview = BackupRestorePreview(
            restaurantName = manifest.restaurantName ?: "Unknown Restaurant",
            createdAt = try { java.time.Instant.parse(manifest.createdAtUtc).toEpochMilli() } catch (e: Exception) { null },
            backupFormatVersion = manifest.backupFormatVersion,
            databaseSchemaVersion = manifest.databaseSchemaVersion,
            localeTag = manifest.localeTag ?: "en-US",
            tableCounts = manifest.tableMetadata.mapValues { it.value.entryCount },
            attachmentCount = attachmentSummaries.size,
            totalAttachmentBytes = attachmentSummaries.sumOf { it.sizeBytes }
        )

        BackupArchiveInspectionResult.Ready(
            archive = InspectedBackupArchive(
                snapshot = snapshot,
                preferences = preferences,
                manifest = manifest,
                attachmentSummaries = attachmentSummaries,
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
