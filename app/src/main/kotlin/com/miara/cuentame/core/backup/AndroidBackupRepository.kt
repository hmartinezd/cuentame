package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val restaurantRepository: RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider,
    private val checksumProvider: ChecksumProvider
) : BackupRepository {

    private val backupWriterJson = Json {
        prettyPrint = true
        encodeDefaults = true
        isLenient = false
        ignoreUnknownKeys = false
    }

    private val backupReaderJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    override fun createBackup(destinationUri: String): Flow<BackupOperationStatus> = flow {
        emit(BackupOperationStatus.Creating)
        try {
            val manifest = writeBackupToDestination(destinationUri)
            emit(BackupOperationStatus.Validating)
            when (val validation = validateBackup(destinationUri)) {
                is BackupValidationResult.Valid -> emit(BackupOperationStatus.Success(validation.manifest))
                is BackupValidationResult.Invalid -> emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure(validation.code, validation.reason)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupCreationException) {
            emit(BackupOperationStatus.Error(e.error))
        } catch (e: IOException) {
            if (isNoSpaceError(e)) {
                emit(BackupOperationStatus.Error(BackupResult.Error.InsufficientStorage))
            } else {
                emit(BackupOperationStatus.Error(BackupResult.Error.SystemIOFailure(e)))
            }
        } catch (e: SecurityException) {
            emit(BackupOperationStatus.Error(BackupResult.Error.PermissionDenied))
        } catch (e: Exception) {
            emit(BackupOperationStatus.Error(BackupResult.Error.SystemIOFailure(e)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun writeBackupToDestination(destinationUri: String): BackupManifest {
        val uri = Uri.parse(destinationUri)
        val outputStream = try {
            context.contentResolver.openOutputStream(uri)
                ?: throw BackupCreationException(BackupResult.Error.DestinationUnavailable)
        } catch (e: SecurityException) {
            throw BackupCreationException(BackupResult.Error.PermissionDenied)
        } catch (e: BackupCreationException) {
            throw e
        } catch (e: Exception) {
            throw BackupCreationException(BackupResult.Error.DestinationUnavailable)
        }

        val restaurant = restaurantRepository.getRestaurant()
            ?: throw BackupCreationException(BackupResult.Error.UnsupportedPersistentData)

        val prefs = preferencesRepository.observePreferences().first()
        val snapshot = backupDao.createSnapshot(restaurant.id.value)

        val attachmentIdMap = mutableMapOf<String, String>()
        snapshot.purchaseReceipts.forEach { receipt ->
            receipt.attachmentPath?.let { path ->
                val uri = Uri.parse(path)
                attachmentIdMap[path] = checksumProvider.computeAttachmentId(uri)
            }
        }
        snapshot.wasteEvents.forEach { waste ->
            waste.attachmentPath?.let { path ->
                val uri = Uri.parse(path)
                attachmentIdMap[path] = checksumProvider.computeAttachmentId(uri)
            }
        }

        val snapshotDto = BackupMapper.mapToDto(snapshot, attachmentIdMap)

        var currentTotalUncompressedBytes = 0L

        ZipOutputStream(outputStream).use { zos ->
            val entryChecksums = mutableMapOf<String, String>()

            // Collect attachment references
            val pendingAttachments = mutableMapOf<String, PendingAttachment>()
            snapshot.purchaseReceipts.forEach { receipt ->
                receipt.attachmentPath?.let { path ->
                    val sourceUri = Uri.parse(path)
                    val id = checksumProvider.computeAttachmentId(sourceUri)
                    val ref = BackupAttachmentReference("PURCHASE_RECEIPT", receipt.id)
                    pendingAttachments.getOrPut(id) { PendingAttachment(sourceUri, id, mutableListOf()) }.references.add(ref)
                }
            }
            snapshot.wasteEvents.forEach { waste ->
                waste.attachmentPath?.let { path ->
                    val sourceUri = Uri.parse(path)
                    val id = checksumProvider.computeAttachmentId(sourceUri)
                    val ref = BackupAttachmentReference("WASTE_EVENT", waste.id)
                    pendingAttachments.getOrPut(id) { PendingAttachment(sourceUri, id, mutableListOf()) }.references.add(ref)
                }
            }

            if (pendingAttachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Maximum attachment count exceeded"))
            }

            // 1. data/database.json
            val contextManifest = BackupManifest(
                backupFormatVersion = BACKUP_FORMAT_VERSION,
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
                includedSections = emptyList()
            )
            val integrityResult = BackupSnapshotIntegrityValidator.validate(snapshotDto, contextManifest)
            if (integrityResult.isFailure) {
                throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure(integrityResult.exceptionOrNull()!!))
            }

            val snapshotJson = backupWriterJson.encodeToString(snapshotDto)
            val snapshotBytes = snapshotJson.toByteArray(Charsets.UTF_8)
            if (snapshotBytes.size > BackupLimits.MAX_SINGLE_JSON_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Database JSON too large"))
            }
            if (currentTotalUncompressedBytes + snapshotBytes.size > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total uncompressed size limit exceeded"))
            }
            currentTotalUncompressedBytes += snapshotBytes.size
            writeZipEntry(zos, "data/database.json", snapshotBytes, entryChecksums)

            // 2. preferences/settings.json
            val prefsDto = BackupPreferencesDto(
                themeMode = prefs.themeMode.name,
                dynamicColorEnabled = prefs.dynamicColorEnabled,
                appLocaleTag = prefs.appLocaleTag
            )
            val prefsJson = backupWriterJson.encodeToString(prefsDto)
            val prefsBytes = prefsJson.toByteArray(Charsets.UTF_8)
            if (prefsBytes.size > BackupLimits.MAX_SINGLE_JSON_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Preferences JSON too large"))
            }
            if (currentTotalUncompressedBytes + prefsBytes.size > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total uncompressed size limit exceeded"))
            }
            currentTotalUncompressedBytes += prefsBytes.size
            writeZipEntry(zos, "preferences/settings.json", prefsBytes, entryChecksums)

            // 3. attachments (sorted by ID)
            val attachmentMetadatas = mutableListOf<BackupAttachmentMetadata>()
            val sortedPending = pendingAttachments.values.sortedBy { it.attachmentId }

            for (pending in sortedPending) {
                try {
                    openAttachmentStream(pending.sourceUri)?.use { inputStream ->
                        val originalDisplayName = queryDisplayName(pending.sourceUri)
                        val effectiveDisplayName = AttachmentFilenameSanitizer.sanitize(originalDisplayName)
                        val archivePath = "attachments/${pending.attachmentId}/$effectiveDisplayName"

                        if (!ArchiveEntryValidator.isSafe(archivePath)) {
                            throw BackupCreationException(BackupResult.Error.ArchiveValidationFailure(BackupValidationCode.UNSAFE_ENTRY_PATH, "Unsafe archive path generated"))
                        }

                        zos.putNextEntry(ZipEntry(archivePath).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })

                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        val buffer = ByteArray(8192)
                        var n: Int
                        while (inputStream.read(buffer).also { n = it } != -1) {
                            if (currentTotalUncompressedBytes + n > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total uncompressed size limit exceeded"))
                            }
                            digest.update(buffer, 0, n)
                            zos.write(buffer, 0, n)
                            size += n
                            currentTotalUncompressedBytes += n
                        }
                        zos.closeEntry()

                        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                        entryChecksums[archivePath] = checksum

                        attachmentMetadatas.add(BackupAttachmentMetadata(
                            attachmentId = pending.attachmentId,
                            archivePath = archivePath,
                            displayName = effectiveDisplayName,
                            mimeType = context.contentResolver.getType(pending.sourceUri),
                            sizeBytes = size,
                            checksumSha256 = checksum,
                            referencedBy = pending.references.sortedWith(compareBy({ it.recordType }, { it.recordId }))
                        ))
                    } ?: throw BackupCreationException(BackupResult.Error.MissingAttachment(pending.attachmentId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    throw BackupCreationException(BackupResult.Error.PermissionDenied)
                } catch (e: IOException) {
                    if (isNoSpaceError(e)) {
                        throw BackupCreationException(BackupResult.Error.InsufficientStorage)
                    } else {
                        throw BackupCreationException(BackupResult.Error.UnreadableAttachment(pending.attachmentId, e))
                    }
                } catch (e: Exception) {
                    if (e is BackupCreationException) throw e
                    throw BackupCreationException(BackupResult.Error.UnreadableAttachment(pending.attachmentId, e))
                }
            }

            // 4. Create Manifest
            val manifest = BackupManifest(
                backupFormatVersion = BACKUP_FORMAT_VERSION,
                createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(timeProvider.now()),
                applicationId = appVersionProvider.applicationId,
                appVersionName = appVersionProvider.versionName,
                appVersionCode = appVersionProvider.versionCode,
                databaseSchemaVersion = appVersionProvider.databaseSchemaVersion,
                restaurantId = restaurant.id.value,
                restaurantName = restaurant.name,
                localeTag = restaurant.localeTag,
                currencyCode = restaurant.currencyCode,
                tableMetadata = createTableMetadata(snapshotDto).entries.sortedBy { it.key }.associate { it.key to it.value },
                attachments = attachmentMetadatas.sortedBy { it.attachmentId },
                includedSections = listOf("data", "preferences", "attachments").sorted(),
                checksumAlgorithm = "SHA-256"
            )
            val manifestJson = backupWriterJson.encodeToString(manifest)
            val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
            if (manifestBytes.size > BackupLimits.MAX_SINGLE_JSON_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Manifest JSON too large"))
            }
            if (currentTotalUncompressedBytes + manifestBytes.size > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total uncompressed size limit exceeded"))
            }
            currentTotalUncompressedBytes += manifestBytes.size
            writeZipEntry(zos, "manifest.json", manifestBytes, entryChecksums)

            // 5. checksums.json
            val sortedChecksums = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
            val checksumsJson = backupWriterJson.encodeToString(sortedChecksums)
            val checksumsBytes = checksumsJson.toByteArray(Charsets.UTF_8)
            if (checksumsBytes.size > BackupLimits.MAX_SINGLE_JSON_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Checksums JSON too large"))
            }
            if (currentTotalUncompressedBytes + checksumsBytes.size > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw BackupCreationException(BackupResult.Error.LimitExceeded("Total uncompressed size limit exceeded"))
            }
            currentTotalUncompressedBytes += checksumsBytes.size
            writeZipEntry(zos, "checksums.json", checksumsBytes, mutableMapOf())

            return manifest
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray, checksums: MutableMap<String, String>) {
        if (name.length > BackupLimits.MAX_ENTRY_NAME_LENGTH) {
            throw BackupCreationException(BackupResult.Error.ArchiveValidationFailure(BackupValidationCode.UNSAFE_ENTRY_PATH, "Archive entry name too long"))
        }
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        zos.write(bytes)
        zos.closeEntry()
        if (name != "checksums.json") {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            checksums[name] = digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun validateBackup(sourceUri: String): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(sourceUri)
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Could not open source URI")
            stream.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    validateArchiveStream(zis)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Validation read failed")
        }
    }

    private fun validateArchiveStream(zis: ZipInputStream): BackupValidationResult {
        val entryMetadata = mutableMapOf<String, EntryInfo>()
        val jsonPayloads = mutableMapOf<String, String>()

        var totalEntries = 0
        var totalUncompressedSize = 0L

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            totalEntries++
            if (totalEntries > BackupLimits.MAX_ENTRY_COUNT) {
                return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "Archive entry count limit exceeded")
            }
            if (name.length > BackupLimits.MAX_ENTRY_NAME_LENGTH) {
                return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH, "Archive entry name too long")
            }

            if (entryMetadata.containsKey(name)) {
                return BackupValidationResult.Invalid(BackupValidationCode.DUPLICATE_ENTRY, "Duplicate entry detected")
            }
            if (!ArchiveEntryValidator.isSafe(name)) {
                return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH, "Invalid zip entry path")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(8192)
            val jsonBuffer = if (name.endsWith(".json")) ByteArrayOutputStream() else null

            var n: Int
            while (zis.read(buffer).also { n = it } != -1) {
                if (totalUncompressedSize + n > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "Total uncompressed size limit exceeded")
                }
                digest.update(buffer, 0, n)
                size += n
                totalUncompressedSize += n

                jsonBuffer?.write(buffer, 0, n)
                if (jsonBuffer != null && size > BackupLimits.MAX_SINGLE_JSON_BYTES) {
                    return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, "JSON payload size limit exceeded")
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            entryMetadata[name] = EntryInfo(name, checksum, size)
            if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")

            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing manifest.json")
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing checksums.json")
        val settingsJson = jsonPayloads["preferences/settings.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing preferences/settings.json")
        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing data/database.json")

        // 1. Exact archive entry set for format v1
        val expectedBaseEntries = setOf("manifest.json", "checksums.json", "preferences/settings.json", "data/database.json")
        val manifest = try {
            backupReaderJson.decodeFromString<BackupManifest>(manifestJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Malformed manifest.json")
        }
        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, "Manifest invalid")
        }

        val attachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val totalExpectedEntries = expectedBaseEntries + attachmentPaths

        if (entryMetadata.keys != totalExpectedEntries) {
            val unexpected = entryMetadata.keys - totalExpectedEntries
            if (unexpected.isNotEmpty()) {
                return BackupValidationResult.Invalid(BackupValidationCode.UNEXPECTED_ENTRY, "Unexpected archive entries found")
            }
            return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY, "Missing required archive entries")
        }

        // 2. Strict Checksums validation
        val reportedChecksumsResult = ChecksumParser.parse(checksumsJson)
        if (reportedChecksumsResult.isFailure) {
            return BackupValidationResult.Invalid(
                BackupValidationCode.CHECKSUM_PARSE_FAILURE,
                reportedChecksumsResult.exceptionOrNull()?.message ?: "Invalid checksums.json"
            )
        }
        val reportedChecksums = reportedChecksumsResult.getOrThrow()

        // Require checksum key set to equal exactly archive entries - checksums.json
        val expectedChecksumKeys = entryMetadata.keys - "checksums.json"
        if (reportedChecksums.keys != expectedChecksumKeys) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH, "Checksums key set mismatch")
        }

        for ((name, reportedChecksum) in reportedChecksums) {
            val actual = entryMetadata[name] ?: return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH, "Checksum for nonexistent entry")
            if (actual.checksum != reportedChecksum) {
                return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH, "Checksum mismatch")
            }
        }

        // 3. Preferences Validation
        val prefsDto = try {
            backupReaderJson.decodeFromString<BackupPreferencesDto>(settingsJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Invalid preferences/settings.json")
        }
        try {
            val locale = java.util.Locale.forLanguageTag(prefsDto.appLocaleTag)
            if (locale.language.isBlank()) {
                return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Unsupported app locale")
            }
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Unsupported app locale")
        }
        try {
            com.miara.cuentame.core.preferences.model.ThemeMode.valueOf(prefsDto.themeMode)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID, "Unsupported theme mode")
        }

        // 4. Database Integrity
        val dbDto = try {
            backupReaderJson.decodeFromString<BackupSnapshotDto>(dbJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, "Malformed data/database.json")
        }

        val actualCounts = createTableMetadata(dbDto)
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (actualCounts[tableName]?.entryCount != metadata.entryCount) {
                return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, "Entry count mismatch for table: $tableName")
            }
        }

        val integrityResult = BackupSnapshotIntegrityValidator.validate(dbDto, manifest)
        if (integrityResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, integrityResult.exceptionOrNull()?.message ?: "Snapshot integrity failed")
        }

        // 5. Attachment multiplicity and relationships
        val manifestAttachmentIds = manifest.attachments.map { it.attachmentId }
        if (manifestAttachmentIds.distinct().size != manifestAttachmentIds.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Duplicate attachment IDs in manifest")
        }

        val manifestArchivePaths = manifest.attachments.map { it.archivePath }
        if (manifestArchivePaths.distinct().size != manifestArchivePaths.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Duplicate archive paths in manifest")
        }

        val expectedRefs = mutableSetOf<String>()
        dbDto.purchaseReceipts.forEach { r -> r.attachmentId?.let { expectedRefs.add("$it|PURCHASE_RECEIPT|${r.id}") } }
        dbDto.wasteEvents.forEach { w -> w.attachmentId?.let { expectedRefs.add("$it|WASTE_EVENT|${w.id}") } }

        val manifestRefs = manifest.attachments.flatMap { a ->
            if (a.referencedBy.isEmpty()) throw Exception("Attachment ${a.attachmentId} has no references")
            a.referencedBy.map { "${a.attachmentId}|${it.recordType}|${it.recordId}" }
        }.toSet()

        if (expectedRefs != manifestRefs) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment reference mismatch")
        }

        val dbAttIds = (dbDto.purchaseReceipts.mapNotNull { it.attachmentId } + dbDto.wasteEvents.mapNotNull { it.attachmentId }).toSet()
        if (manifestAttachmentIds.toSet() != dbAttIds) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment ID set mismatch between manifest and database")
        }

        for (attachment in manifest.attachments) {
            if (attachment.displayName.isBlank()) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment display name is blank")
            }
            if (!AttachmentFilenameSanitizer.isValid(attachment.displayName)) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Invalid attachment display name")
            }
            val actual = entryMetadata[attachment.archivePath] ?: return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Missing attachment file")
            if (actual.size != attachment.sizeBytes) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment size mismatch")
            }
            if (actual.checksum != attachment.checksumSha256) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Attachment checksum mismatch")
            }

            val expectedPath = "attachments/${attachment.attachmentId}/${attachment.displayName}"
            if (attachment.archivePath != expectedPath) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, "Mismatched attachment path")
            }
        }

        return BackupValidationResult.Valid(manifest)
    }

    private fun isNoSpaceError(e: IOException): Boolean {
        val msg = e.message ?: return false
        return msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space left on device", ignoreCase = true)
    }

    private fun openAttachmentStream(sourceUri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(sourceUri)
        } catch (e: Exception) {
            if (sourceUri.scheme == "file" && sourceUri.path != null) {
                val file = File(sourceUri.path!!)
                if (file.exists()) file.inputStream() else null
            } else null
        } ?: run {
            if (sourceUri.scheme == "file" && sourceUri.path != null) {
                val file = File(sourceUri.path!!)
                if (file.exists()) file.inputStream() else null
            } else null
        }
    }

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

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
    private data class PendingAttachment(
        val sourceUri: Uri,
        val attachmentId: String,
        val references: MutableList<BackupAttachmentReference>
    )

    private class BackupCreationException(val error: BackupResult.Error) : Exception()

    companion object {
        private const val BACKUP_FORMAT_VERSION = 1
        private const val DETERMINISTIC_ZIP_TIMESTAMP = 1767225600000L // 2026-01-01T00:00:00Z
    }
}
