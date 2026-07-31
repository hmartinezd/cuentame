package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityException
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityValidator
import com.miara.cuentame.core.backup.ChecksumParser
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.BackupManifestContractValidator
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupArchiveRestoreStager @Inject constructor(
    private val codecs: BackupJsonCodecs,
    private val processor: BackupArchiveProcessor,
    private val storage: InternalBackupRestoreStorage,
    private val fingerprinter: BackupArchiveFingerprinter
) {
    suspend fun stage(
        sessionId: String,
        input: InputStream
    ): BackupArchiveStagingResult = withContext(Dispatchers.IO) {
        val stagingDir = storage.getStagingDir(sessionId)
        val coreEntries = mutableMapOf<String, ByteArray>()
        
        val sink = object : BackupArchiveProcessor.Sink {
            override suspend fun onCoreEntry(name: String, bytes: ByteArray) {
                coreEntries[name] = bytes
            }

            override suspend fun onAttachment(name: String, inputStream: InputStream, expectedSize: Long) {
                // name is "attachments/<id>/<displayName>"
                // We want to store it in stagingDir/<id>/<displayName>
                val relativePath = name.removePrefix("attachments/")
                val targetFile = File(stagingDir, relativePath)
                targetFile.parentFile?.mkdirs()
                
                FileOutputStream(targetFile).use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = inputStream.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                    }
                    out.flush()
                }
            }
        }

        val processResult = processor.process(input, sink)
        val (checksums, sizes) = when (processResult) {
            is BackupArchiveProcessingResult.Success -> processResult.checksums to processResult.sizes
            is BackupArchiveProcessingResult.Failure -> return@withContext BackupArchiveStagingResult.Failure(processResult.reason)
        }

        // --- Post-processing and Validation (Same as Reader) ---
        
        val manifestJsonBytes = coreEntries[BackupFormatV1Contract.MANIFEST_ENTRY] ?: return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MissingCoreEntry)
        val dbJsonBytes = coreEntries[BackupFormatV1Contract.DATABASE_ENTRY] ?: return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MissingCoreEntry)
        val prefJsonBytes = coreEntries[BackupFormatV1Contract.PREFERENCES_ENTRY] ?: return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MissingCoreEntry)
        val checksumsJsonBytes = coreEntries[BackupFormatV1Contract.CHECKSUMS_ENTRY] ?: return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MissingCoreEntry)

        // 1. Checksums.json
        val declaredChecksums = try {
            ChecksumParser.parse(checksumsJsonBytes.decodeToString()).getOrElse { return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MalformedChecksums) }
        } catch (e: Exception) {
            return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MalformedChecksums)
        }
        
        // 2. Manifest
        val manifest = try {
            codecs.reader.decodeFromString<BackupManifest>(manifestJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MalformedManifest)
        }

        BackupManifestContractValidator.validateManifestStructure(manifest, checksums, sizes)?.let { 
            return@withContext BackupArchiveStagingResult.Failure(it) 
        }

        // 3. Snapshot
        val snapshot = try {
            codecs.reader.decodeFromString<BackupSnapshotDto>(dbJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MalformedSnapshot)
        }

        BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)?.let {
            return@withContext BackupArchiveStagingResult.Failure(it)
        }

        BackupSnapshotIntegrityValidator.validate(snapshot, manifest).getOrElse { e ->
            val code = (e as? BackupSnapshotIntegrityException)?.code ?: BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE
            return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.SnapshotIntegrityFailure(code))
        }

        // 4. Preferences
        val preferences = try {
            codecs.reader.decodeFromString<BackupPreferencesDto>(prefJsonBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext BackupArchiveStagingResult.Failure(BackupRestoreFailure.MalformedPreferences)
        }

        BackupArchiveStagingResult.Success(
            snapshot = snapshot,
            preferences = preferences,
            manifest = manifest,
            fingerprint = fingerprinter.calculate(manifest, declaredChecksums),
            stagingDir = stagingDir
        )
    }
}

sealed interface BackupArchiveStagingResult {
    data class Success(
        val snapshot: BackupSnapshotDto,
        val preferences: BackupPreferencesDto,
        val manifest: BackupManifest,
        val fingerprint: BackupArchiveFingerprint,
        val stagingDir: File
    ) : BackupArchiveStagingResult
    data class Failure(val reason: BackupRestoreFailure) : BackupArchiveStagingResult
}
