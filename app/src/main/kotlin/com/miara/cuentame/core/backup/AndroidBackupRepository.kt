package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class AndroidBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val restaurantRepository: com.miara.cuentame.core.domain.repository.RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider,
    private val checksumProvider: ChecksumProvider
) : BackupRepository {

    private val backupWriterJson = Json { 
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val backupReaderJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
        explicitNulls = true
    }

    private val DETERMINISTIC_ZIP_TIMESTAMP = 0L

    override fun createBackup(destinationUri: String): Flow<BackupOperationStatus> = flow {
        emit(BackupOperationStatus.Creating)
        val uri = Uri.parse(destinationUri)
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw BackupCreationException(BackupResult.Error.DestinationUnavailable)
            
            val entryChecksums = mutableMapOf<String, String>()

            pfd.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { fos ->
                    BufferedOutputStream(fos).use { bos ->
                        ZipOutputStream(bos).use { zos ->
                            zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                            performBackup(zos, entryChecksums)
                        }
                    }
                }
            }
            
            emit(BackupOperationStatus.Validating)
            val validation = validateBackup(destinationUri)
            if (validation is BackupValidationResult.Valid) {
                emit(BackupOperationStatus.Success(validation.manifest))
            } else {
                val invalid = validation as BackupValidationResult.Invalid
                tryCleanupPartialFile(uri)
                emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure(invalid.code, invalid.reason)))
            }
        } catch (e: CancellationException) {
            tryCleanupPartialFile(uri)
            throw e
        } catch (e: BackupCreationException) {
            tryCleanupPartialFile(uri)
            emit(BackupOperationStatus.Error(e.error))
        } catch (e: SecurityException) {
            tryCleanupPartialFile(uri)
            emit(BackupOperationStatus.Error(BackupResult.Error.PermissionDenied))
        } catch (e: Exception) {
            tryCleanupPartialFile(uri)
            if (isInsufficientStorage(e)) {
                emit(BackupOperationStatus.Error(BackupResult.Error.InsufficientStorage))
            } else {
                emit(BackupOperationStatus.Error(BackupResult.Error.SystemIOFailure(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun isInsufficientStorage(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is IOException && cause.message?.contains("ENOSPC", ignoreCase = true) == true) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun tryCleanupPartialFile(uri: Uri) {
        try {
            // Truncate to zero bytes if possible
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { it.close() }
        } catch (e: Exception) {
            // Best effort cleanup
        }
    }

    private suspend fun performBackup(zos: ZipOutputStream, entryChecksums: MutableMap<String, String>) {
        val restaurant = restaurantRepository.getRestaurant() 
            ?: throw BackupCreationException(BackupResult.Error.RestaurantUnavailable)
            
        val snapshot = try {
            backupDao.createSnapshot(restaurant.id.value)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure(e)) }
        
        val prefs = try {
            preferencesRepository.observePreferences().first()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw BackupCreationException(BackupResult.Error.PreferencesReadFailure(e)) }
        
        // 1. Identify and multiplicity check for attachments
        val pendingAttachments = mutableMapOf<String, PendingAttachment>() 
        
        fun addReference(uriString: String?, type: String, recordId: String) {
            if (uriString.isNullOrBlank()) return
            val pending = pendingAttachments.getOrPut(uriString) {
                PendingAttachment(
                    sourceUri = Uri.parse(uriString),
                    attachmentId = checksumProvider.computeAttachmentId(uriString),
                    references = mutableListOf()
                )
            }
            pending.references.add(BackupAttachmentReference(type, recordId))
        }

        snapshot.purchaseReceipts.forEach { addReference(it.attachmentPath, "PURCHASE_RECEIPT", it.id) }
        snapshot.wasteEvents.forEach { addReference(it.attachmentPath, "WASTE_EVENT", it.id) }

        if (pendingAttachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
            throw BackupCreationException(BackupResult.Error.LimitExceeded("Too many attachments"))
        }

        // 2. Preflight DTO Integrity Check
        val uriToAttachmentId = pendingAttachments.mapValues { it.value.attachmentId }
        val snapshotDto = BackupMapper.mapToDto(snapshot, uriToAttachmentId)
        
        val contextManifest = createBaseManifest(restaurant)
        BackupSnapshotIntegrityValidator.validate(snapshotDto, contextManifest).getOrElse {
            throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure(it))
        }

        var totalUncompressedBytes = 0L

        fun writeJsonEntry(name: String, content: String, limit: Int) {
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (bytes.size > limit) throw BackupCreationException(BackupResult.Error.LimitExceeded("JSON entry $name too large"))
            
            totalUncompressedBytes += bytes.size
            if (totalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total archive size limit exceeded"))
            }
            
            writeZipEntryBytes(zos, name, bytes, entryChecksums)
        }

        // Entry Order: data -> preferences -> attachments (sorted) -> manifest -> checksums
        
        // 1. data/database.json
        writeJsonEntry("data/database.json", backupWriterJson.encodeToString(snapshotDto), BackupLimits.MAX_DATABASE_JSON_BYTES)
        
        // 2. preferences/settings.json
        val prefsDto = BackupPreferencesDto(
            themeMode = prefs.themeMode.name,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            appLocaleTag = prefs.appLocaleTag
        )
        writeJsonEntry("preferences/settings.json", backupWriterJson.encodeToString(prefsDto), BackupLimits.MAX_SETTINGS_JSON_BYTES)
        
        // 3. attachments
        val attachmentMetadatas = mutableListOf<BackupAttachmentMetadata>()
        val sortedPending = pendingAttachments.values.sortedBy { it.attachmentId }
        
        for (pending in sortedPending) {
            try {
                context.contentResolver.openInputStream(pending.sourceUri)?.use { inputStream ->
                    val originalDisplayName = queryDisplayName(pending.sourceUri)
                    val effectiveDisplayName = AttachmentFilenameSanitizer.sanitize(originalDisplayName)
                    val archivePath = "attachments/${pending.attachmentId}/$effectiveDisplayName"
                    
                    if (!ArchiveEntryValidator.isSafe(archivePath)) throw Exception("Unsafe archive path")

                    zos.putNextEntry(ZipEntry(archivePath).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
                    
                    val digest = MessageDigest.getInstance("SHA-256")
                    var entrySize = 0L
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (inputStream.read(buffer).also { n = it } != -1) {
                        digest.update(buffer, 0, n)
                        zos.write(buffer, 0, n)
                        entrySize += n
                        totalUncompressedBytes += n
                        if (totalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw BackupCreationException(BackupResult.Error.LimitExceeded("Total archive size limit exceeded"))
                        }
                    }
                    zos.closeEntry()
                    
                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    entryChecksums[archivePath] = checksum

                    attachmentMetadatas.add(BackupAttachmentMetadata(
                        attachmentId = pending.attachmentId,
                        archivePath = archivePath,
                        displayName = effectiveDisplayName,
                        mimeType = context.contentResolver.getType(pending.sourceUri),
                        sizeBytes = entrySize,
                        checksumSha256 = checksum,
                        referencedBy = pending.references.sortedWith(compareBy({ it.recordType }, { it.recordId }))
                    ))
                } ?: throw BackupCreationException(BackupResult.Error.MissingAttachment(pending.attachmentId))
            } catch (e: CancellationException) { throw e }
            catch (e: BackupCreationException) { throw e }
            catch (e: Exception) { throw BackupCreationException(BackupResult.Error.UnreadableAttachment(pending.attachmentId, e)) }
        }
        
        // 4. manifest.json
        val finalManifest = contextManifest.copy(
            tableMetadata = createTableMetadata(snapshotDto).entries.sortedBy { it.key }.associate { it.key to it.value },
            attachments = attachmentMetadatas.sortedBy { it.attachmentId },
            includedSections = listOf("data", "preferences", "attachments").sorted()
        )
        writeJsonEntry("manifest.json", backupWriterJson.encodeToString(finalManifest), BackupLimits.MAX_MANIFEST_JSON_BYTES)
        
        // 5. checksums.json
        val sortedChecksumMap = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
        val checksumsJson = backupWriterJson.encodeToString(sortedChecksumMap)
        writeJsonEntry("checksums.json", checksumsJson, BackupLimits.MAX_CHECKSUMS_JSON_BYTES)
    }

    private fun createBaseManifest(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) = BackupManifest(
        backupFormatVersion = BackupLimits.BACKUP_FORMAT_VERSION,
        createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(timeProvider.now()),
        applicationId = appVersionProvider.applicationId,
        appVersionName = appVersionProvider.versionName,
        appVersionCode = appVersionProvider.versionCode,
        databaseSchemaVersion = appVersionProvider.databaseSchemaVersion,
        restaurantId = restaurant.id.value,
        restaurantName = restaurant.name,
        localeTag = restaurant.localeTag,
        currencyCode = restaurant.currencyCode,
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = emptyList(),
        checksumAlgorithm = "SHA-256"
    )

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    private fun writeZipEntryBytes(zos: ZipOutputStream, name: String, bytes: ByteArray, checksums: MutableMap<String, String>) {
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        zos.write(bytes)
        zos.closeEntry()
        if (name != "checksums.json") {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            checksums[name] = digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private data class PendingAttachment(
        val sourceUri: Uri,
        val attachmentId: String,
        val references: MutableList<BackupAttachmentReference>
    )

    private fun createTableMetadata(dto: BackupSnapshotDto) = mapOf(
        "restaurants" to TableMetadata(dto.restaurants.size, false),
        "inventory_areas" to TableMetadata(dto.inventoryAreas.size, false),
        "ingredient_categories" to TableMetadata(dto.ingredientCategories.size, false),
        "units" to TableMetadata(dto.units.size, false),
        "ingredients" to TableMetadata(dto.ingredients.size, false),
        "ingredient_unit_options" to TableMetadata(dto.ingredientUnitOptions.size, false),
        "suppliers" to TableMetadata(dto.suppliers.size, false),
        "purchase_receipts" to TableMetadata(dto.purchaseReceipts.size, false),
        "purchase_lines" to TableMetadata(dto.purchaseLines.size, false),
        "stock_counts" to TableMetadata(dto.stockCounts.size, false),
        "stock_count_areas" to TableMetadata(dto.stockCountAreas.size, false),
        "stock_count_lines" to TableMetadata(dto.stockCountLines.size, false),
        "waste_events" to TableMetadata(dto.wasteEvents.size, false),
        "inventory_movements" to TableMetadata(dto.inventoryMovements.size, false),
        "inventory_balance_projections" to TableMetadata(dto.inventoryBalanceProjections.size, true),
        "ingredient_cost_projections" to TableMetadata(dto.ingredientCostProjections.size, true)
    )

    override suspend fun validateBackup(sourceUri: String): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(sourceUri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    validateArchiveStream(zis)
                }
            } ?: BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR, "Could not open source URI")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR, "Validation failed: ${e.message}")
        }
    }

    private fun validateArchiveStream(zis: ZipInputStream): BackupValidationResult {
        val entryMetadata = mutableMapOf<String, EntryInfo>()
        val jsonPayloads = mutableMapOf<String, String>()
        
        var totalEntries = 0
        var currentTotalUncompressedSize = 0L

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            totalEntries++
            if (totalEntries > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "Archive entry count limit exceeded")
            if (name.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH, "Archive entry name too long: $name")
            
            if (entryMetadata.containsKey(name)) return BackupValidationResult.Invalid(BackupValidationCode.DUPLICATE_ENTRY, "Duplicate entry: $name")
            if (!ArchiveEntryValidator.isSafe(name)) return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH, "Unsafe entry name: $name")

            val digest = MessageDigest.getInstance("SHA-256")
            var entrySize = 0L
            val buffer = ByteArray(8192)
            val isJson = name.endsWith(".json")
            val jsonBuffer = if (isJson) ByteArrayOutputStream() else null
            
            var n: Int
            while (zis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
                entrySize += n
                currentTotalUncompressedSize += n
                if (currentTotalUncompressedSize > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "Total uncompressed size limit exceeded")
                
                jsonBuffer?.write(buffer, 0, n)
                if (isJson) {
                    val limit = when(name) {
                        "manifest.json" -> BackupLimits.MAX_MANIFEST_JSON_BYTES
                        "preferences/settings.json" -> BackupLimits.MAX_SETTINGS_JSON_BYTES
                        "data/database.json" -> BackupLimits.MAX_DATABASE_JSON_BYTES
                        "checksums.json" -> BackupLimits.MAX_CHECKSUMS_JSON_BYTES
                        else -> 10 * 1024 * 1024 // 10MB fallback
                    }
                    if (entrySize > limit) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "JSON payload too large: $name")
                }
            }
            
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            entryMetadata[name] = EntryInfo(name, checksum, entrySize)
            if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")
            
            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing manifest.json")
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing checksums.json")
        val settingsJson = jsonPayloads["preferences/settings.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing preferences/settings.json")
        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing data/database.json")
        
        // 1. Strict Manifest Validation
        val manifest = try { 
            backupReaderJson.decodeFromString<BackupManifest>(manifestJson) 
        } catch (e: Exception) { return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Malformed manifest.json: ${e.message}") }
        
        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult.isFailure) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Manifest invalid: ${manifestResult.exceptionOrNull()?.message}")

        // 2. Exact Archive Entry Set
        val expectedBaseEntries = setOf("manifest.json", "checksums.json", "preferences/settings.json", "data/database.json")
        val attachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val totalExpectedEntries = expectedBaseEntries + attachmentPaths
        
        if (entryMetadata.keys != totalExpectedEntries) {
            val unexpected = entryMetadata.keys - totalExpectedEntries
            if (unexpected.isNotEmpty()) return BackupValidationResult.Invalid(BackupValidationCode.UNEXPECTED_ENTRY, "Unexpected entries: $unexpected")
            val missing = totalExpectedEntries - entryMetadata.keys
            return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing expected entries: $missing")
        }

        // 3. Strict Checksums Validation (using Parser)
        val reportedChecksumsResult = ChecksumParser.parse(checksumsJson)
        if (reportedChecksumsResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_PARSE_FAILURE, reportedChecksumsResult.exceptionOrNull()?.message!!)
        }
        val reportedChecksums = reportedChecksumsResult.getOrThrow()

        if (reportedChecksums.size != entryMetadata.size - 1) { // exclude checksums.json itself
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH, "Checksum key count mismatch")
        }

        for ((name, reportedChecksum) in reportedChecksums) {
            val actual = entryMetadata[name] ?: return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH, "Checksum for nonexistent entry: $name")
            if (actual.checksum != reportedChecksum) return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH, "Checksum mismatch for $name")
        }

        for (name in entryMetadata.keys) {
            if (name == "checksums.json") continue
            if (!reportedChecksums.containsKey(name)) return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH, "Entry $name missing from checksums.json")
        }

        // 4. Preferences & Database Integrity
        val prefsDto = try {
            backupReaderJson.decodeFromString<BackupPreferencesDto>(settingsJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Invalid preferences/settings.json: ${e.message}")
        }
        if (prefsDto.appLocaleTag !in setOf("en-US", "es-US")) return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Unsupported app locale: ${prefsDto.appLocaleTag}")
        try { com.miara.cuentame.core.preferences.model.ThemeMode.valueOf(prefsDto.themeMode) }
        catch (e: Exception) { return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Unsupported theme mode") }

        val dbDto = try { 
            backupReaderJson.decodeFromString<BackupSnapshotDto>(dbJson) 
        } catch (e: Exception) { return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, "Malformed data/database.json: ${e.message}") }
        
        val actualCounts = createTableMetadata(dbDto)
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (actualCounts[tableName]?.entryCount != metadata.entryCount) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Entry count mismatch for table: $tableName")
            }
        }
        
        val integrityResult = BackupSnapshotIntegrityValidator.validate(dbDto, manifest)
        if (integrityResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, "Snapshot integrity failed: ${integrityResult.exceptionOrNull()?.message}")
        }

        // 5. Attachment Multiplicity and Multi-reference Consistency
        val manifestAttachments = manifest.attachments
        if (manifestAttachments.map { it.attachmentId }.distinct().size != manifestAttachments.size) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Duplicate attachment IDs in manifest")
        if (manifestAttachments.map { it.archivePath }.distinct().size != manifestAttachments.size) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Duplicate archive paths in manifest")
        
        if (manifestAttachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "Too many attachments in manifest")

        val expectedRefs = mutableSetOf<String>()
        dbDto.purchaseReceipts.forEach { r -> r.attachmentId?.let { expectedRefs.add("$it|PURCHASE_RECEIPT|${r.id}") } }
        dbDto.wasteEvents.forEach { w -> w.attachmentId?.let { expectedRefs.add("$it|WASTE_EVENT|${w.id}") } }

        val manifestRefs = manifestAttachments.flatMap { a -> 
            if (a.referencedBy.isEmpty()) throw Exception("Attachment ${a.attachmentId} has no references")
            a.referencedBy.map { "${a.attachmentId}|${it.recordType}|${it.recordId}" } 
        }.toSet()
        
        if (expectedRefs != manifestRefs) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment reference set mismatch between database and manifest")
        }

        val dbAttIds = (dbDto.purchaseReceipts.mapNotNull { it.attachmentId } + dbDto.wasteEvents.mapNotNull { it.attachmentId }).toSet()
        if (manifestAttachments.map { it.attachmentId }.toSet() != dbAttIds) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment ID set mismatch between database and manifest")
        }

        for (attachment in manifestAttachments) {
            if (attachment.displayName.isBlank()) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment display name blank")
            if (!AttachmentFilenameSanitizer.isValid(attachment.displayName)) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Invalid attachment display name format")
            
            val actual = entryMetadata[attachment.archivePath] ?: return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Missing attachment file in archive")
            if (actual.size != attachment.sizeBytes) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment size mismatch")
            if (actual.checksum != attachment.checksumSha256) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment checksum mismatch")
            
            val expectedPath = "attachments/${attachment.attachmentId}/${attachment.displayName}"
            if (attachment.archivePath != expectedPath) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Mismatched attachment path in manifest")
        }

        return BackupValidationResult.Valid(manifest)
    }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
