package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityException
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityValidator
import com.miara.cuentame.core.backup.ChecksumParser
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.BackupArchiveFingerprinter
import com.miara.cuentame.core.backup.internal.BackupArchiveProcessingResult
import com.miara.cuentame.core.backup.internal.BackupArchiveProcessor
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBackupArchiveReader @Inject constructor(
    private val codecs: BackupJsonCodecs,
    private val processor: BackupArchiveProcessor,
    private val fingerprinter: BackupArchiveFingerprinter
) : BackupArchiveReader {

    override suspend fun inspect(
        input: InputStream,
        source: BackupDocumentUri
    ): BackupArchiveInspectionResult {
        val coreEntries = mutableMapOf<String, ByteArray>()
        
        val sink = object : BackupArchiveProcessor.Sink {
            override suspend fun onCoreEntry(name: String, bytes: ByteArray) {
                coreEntries[name] = bytes
            }
            override suspend fun onAttachment(name: String, inputStream: InputStream, expectedSize: Long) {
                // Not staging during inspection
            }
            override fun shouldProcessAttachment(name: String): Boolean = false
        }

        val processResult = processor.process(input, sink)
        
        val (calculatedChecksums, calculatedSizes) = when (processResult) {
            is BackupArchiveProcessingResult.Success -> processResult.checksums to processResult.sizes
            is BackupArchiveProcessingResult.Failure -> return BackupArchiveInspectionResult.Failure(processResult.reason)
        }

        // 1. Core entries existence
        val databaseJsonBytes = coreEntries[BackupFormatV1Contract.DATABASE_ENTRY]
        val preferencesJsonBytes = coreEntries[BackupFormatV1Contract.PREFERENCES_ENTRY]
        val manifestJsonBytes = coreEntries[BackupFormatV1Contract.MANIFEST_ENTRY]
        val checksumsJsonBytes = coreEntries[BackupFormatV1Contract.CHECKSUMS_ENTRY]

        if (databaseJsonBytes == null || preferencesJsonBytes == null || manifestJsonBytes == null || checksumsJsonBytes == null) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MissingCoreEntry)
        }

        // 2. Checksums.json validation
        val declaredChecksums = try {
            val jsonStr = checksumsJsonBytes.decodeToString()
            ChecksumParser.parse(jsonStr).getOrElse {
                return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
            }
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
        }
        
        if (declaredChecksums.containsKey(BackupFormatV1Contract.CHECKSUMS_ENTRY)) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
        }

        val payloadEntries = calculatedChecksums.keys - BackupFormatV1Contract.CHECKSUMS_ENTRY
        if (declaredChecksums.keys != payloadEntries) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
        }
        
        for (entryName in payloadEntries) {
            val declared = declaredChecksums[entryName] ?: return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
            if (!declared.equals(calculatedChecksums[entryName], ignoreCase = true)) {
                return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.ChecksumMismatch)
            }
            if (!BackupFormatV1Contract.isValidChecksum(declared)) {
                return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedChecksums)
            }
        }

        // 3. Manifest parsing and structural validation
        val manifest = try {
            codecs.reader.decodeFromString<BackupManifest>(manifestJsonBytes.decodeToString())
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedManifest)
        }

        BackupManifestContractValidator.validateManifestStructure(manifest, calculatedChecksums, calculatedSizes)?.let { failure ->
            return BackupArchiveInspectionResult.Failure(failure)
        }

        // 4. Snapshot parsing and logical integrity
        val snapshot = try {
            codecs.reader.decodeFromString<BackupSnapshotDto>(databaseJsonBytes.decodeToString())
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedSnapshot)
        }

        BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)?.let { failure ->
            return BackupArchiveInspectionResult.Failure(failure)
        }

        BackupSnapshotIntegrityValidator.validate(snapshot, manifest).getOrElse { e ->
            val code = (e as? BackupSnapshotIntegrityException)?.code ?: BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.SnapshotIntegrityFailure(code))
        }

        // 5. Preferences parsing
        val preferences = try {
            codecs.reader.decodeFromString<BackupPreferencesDto>(preferencesJsonBytes.decodeToString())
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        // 6. Success - Produce preview
        val totalRecordCount = try {
            manifest.tableMetadata
                .filter { it.key !in BackupFormatV1Contract.DERIVED_TABLES }
                .values.fold(0L) { acc, meta -> BackupByteMath.addExact(acc, meta.entryCount.toLong()) }
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
        }

        val totalAttachmentBytes = try {
            manifest.attachments.fold(0L) { acc, att -> BackupByteMath.addExact(acc, att.sizeBytes) }
        } catch (e: Exception) {
            return BackupArchiveInspectionResult.Failure(BackupRestoreFailure.TotalLimitExceeded)
        }

        val preview = BackupRestorePreview(
            restaurantName = manifest.restaurantName!!,
            createdAt = try { java.time.Instant.parse(manifest.createdAtUtc).toEpochMilli() } catch (e: Exception) { null },
            backupFormatVersion = manifest.backupFormatVersion,
            databaseSchemaVersion = manifest.databaseSchemaVersion,
            localeTag = manifest.localeTag!!,
            totalRecordCount = totalRecordCount,
            attachmentCount = manifest.attachments.size,
            totalAttachmentBytes = totalAttachmentBytes
        )

        val eligibility = com.miara.cuentame.core.model.backup.BackupRestoreEligibility.Eligible

        return BackupArchiveInspectionResult.Ready(
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
                source = source,
                fingerprint = fingerprinter.calculate(manifest, declaredChecksums)
            ),
            preview = preview,
            eligibility = eligibility
        )
    }
}
