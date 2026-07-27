package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.backup.BackupAttachmentMetadata
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.backup.TableMetadata
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.FileOutputStream
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
    private val restaurantRepository: com.miara.cuentame.core.domain.repository.RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider,
    private val checksumProvider: ChecksumProvider
) : BackupRepository {

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun createBackup(destinationUri: String): Flow<BackupOperationStatus> = flow {
        emit(BackupOperationStatus.Creating)
        try {
            val uri = Uri.parse(destinationUri)
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw BackupCreationException(BackupResult.Error.DestinationUnavailable)
            
            val entryChecksums = mutableMapOf<String, String>()

            pfd.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { fos ->
                    BufferedOutputStream(fos).use { bos ->
                        ZipOutputStream(bos).use { zos ->
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
                emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure((validation as BackupValidationResult.Invalid).reason)))
            }
        } catch (e: BackupCreationException) {
            emit(BackupOperationStatus.Error(e.error))
        } catch (e: SecurityException) {
            emit(BackupOperationStatus.Error(BackupResult.Error.PermissionDenied))
        } catch (e: Exception) {
            emit(BackupOperationStatus.Error(BackupResult.Error.Unknown(e)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun performBackup(zos: ZipOutputStream, entryChecksums: MutableMap<String, String>) {
        val restaurant = restaurantRepository.getRestaurant() ?: throw BackupCreationException(BackupResult.Error.UnsupportedPersistentData)
        val snapshot = backupDao.createSnapshot(restaurant.id.value)
        val prefs = preferencesRepository.observePreferences().first()
        
        // 1. Identify and process attachments
        val pendingAttachments = mutableMapOf<String, PendingAttachment>() // URI -> PendingAttachment
        
        fun addReference(uriString: String, type: String, recordId: String) {
            if (uriString.isBlank()) return
            val pending = pendingAttachments.getOrPut(uriString) {
                PendingAttachment(
                    sourceUri = Uri.parse(uriString),
                    attachmentId = java.util.UUID.nameUUIDFromBytes(uriString.toByteArray()).toString(),
                    references = mutableListOf()
                )
            }
            pending.references.add(BackupAttachmentReference(type, recordId))
        }

        snapshot.purchaseReceipts.forEach { it.attachmentPath?.let { uri -> addReference(uri, "PURCHASE_RECEIPT", it.id) } }
        snapshot.wasteEvents.forEach { it.attachmentPath?.let { uri -> addReference(uri, "WASTE_EVENT", it.id) } }

        val attachmentMetadatas = mutableListOf<BackupAttachmentMetadata>()
        
        // Deterministic attachment order
        val sortedPending = pendingAttachments.values.sortedBy { it.attachmentId }
        
        for (pending in sortedPending) {
            try {
                context.contentResolver.openInputStream(pending.sourceUri)?.use { inputStream ->
                    val displayName = queryDisplayName(pending.sourceUri)
                    val sanitizedName = ArchiveEntryValidator.sanitize(displayName ?: "file")
                    val archivePath = "attachments/${pending.attachmentId}/$sanitizedName"
                    
                    if (!ArchiveEntryValidator.isSafe(archivePath)) {
                        throw Exception("Unsafe archive path: $archivePath")
                    }

                    // Task 2: One-pass write + checksum
                    zos.putNextEntry(ZipEntry(archivePath).apply { time = 0L })
                    
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (inputStream.read(buffer).also { n = it } != -1) {
                        digest.update(buffer, 0, n)
                        zos.write(buffer, 0, n)
                        size += n
                    }
                    zos.closeEntry()
                    
                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    entryChecksums[archivePath] = checksum

                    attachmentMetadatas.add(BackupAttachmentMetadata(
                        attachmentId = pending.attachmentId,
                        archivePath = archivePath,
                        displayName = displayName,
                        mimeType = context.contentResolver.getType(pending.sourceUri),
                        sizeBytes = size,
                        checksumSha256 = checksum,
                        referencedBy = pending.references
                    ))
                } ?: throw Exception("Could not open attachment: ${pending.sourceUri}")
            } catch (e: Exception) {
                throw BackupCreationException(BackupResult.Error.UnreadableAttachment(pending.attachmentId, e))
            }
        }

        val snapshotDto = BackupMapper.mapToDto(snapshot, pendingAttachments.mapValues { it.value.attachmentId })
        val snapshotJson = json.encodeToString(snapshotDto)
        
        // Deterministic order for ZIP entries
        writeZipEntry(zos, "data/database.json", snapshotJson, entryChecksums)
        
        val prefsMap = mapOf(
            "themeMode" to prefs.themeMode.name,
            "dynamicColorEnabled" to prefs.dynamicColorEnabled.toString(),
            "appLocaleTag" to prefs.appLocaleTag
        ).entries.sortedBy { it.key }.associate { it.key to it.value }
        writeZipEntry(zos, "preferences/settings.json", json.encodeToString(prefsMap), entryChecksums)
        
        val manifest = BackupManifest(
            backupFormatVersion = 1,
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
            attachments = attachmentMetadatas,
            includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
        writeZipEntry(zos, "manifest.json", json.encodeToString(manifest), entryChecksums)
        
        val deterministicChecksums = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
        writeZipEntry(zos, "checksums.json", json.encodeToString(deterministicChecksums), mutableMapOf())
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String, checksums: MutableMap<String, String>) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(name).apply { time = 0L })
        zos.write(bytes)
        zos.closeEntry()
        if (name != "checksums.json") {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
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
            } ?: BackupValidationResult.Invalid("Could not open source URI")
        } catch (e: Exception) {
            BackupValidationResult.Invalid("Validation failed: ${e.message}")
        }
    }

    private fun validateArchiveStream(zis: ZipInputStream): BackupValidationResult {
        val entryMetadata = mutableMapOf<String, EntryInfo>()
        val jsonPayloads = mutableMapOf<String, String>()
        val maxJsonSize = 10 * 1024 * 1024 // 10MB limit

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            if (entryMetadata.containsKey(name)) return BackupValidationResult.Invalid("Duplicate entry: $name")
            if (!ArchiveEntryValidator.isSafe(name)) return BackupValidationResult.Invalid("Unsafe entry name: $name")

            val digest = java.security.MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(8192)
            val jsonBuffer = if (name.endsWith(".json")) java.io.ByteArrayOutputStream() else null
            
            var n: Int
            while (zis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
                size += n
                jsonBuffer?.write(buffer, 0, n)
                if (jsonBuffer != null && size > maxJsonSize) return BackupValidationResult.Invalid("JSON payload too large: $name")
            }
            
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            entryMetadata[name] = EntryInfo(name, checksum, size)
            if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")
            
            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid("Missing manifest.json")
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid("Missing checksums.json")
        
        val manifest = try { json.decodeFromString<BackupManifest>(manifestJson) } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed manifest.json") }
        if (manifest.backupFormatVersion != 1) return BackupValidationResult.Invalid("Unsupported backup format version: ${manifest.backupFormatVersion}")
        if (manifest.applicationId.isBlank()) return BackupValidationResult.Invalid("Manifest application ID is blank")
        if (manifest.restaurantId.isNullOrBlank()) return BackupValidationResult.Invalid("Manifest restaurant ID is blank")

        val reportedChecksums = try { json.decodeFromString<Map<String, String>>(checksumsJson) } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed checksums.json") }

        for ((name, reportedChecksum) in reportedChecksums) {
            val actual = entryMetadata[name] ?: return BackupValidationResult.Invalid("Checksum for nonexistent entry: $name")
            if (actual.checksum != reportedChecksum) return BackupValidationResult.Invalid("Checksum mismatch for $name")
        }

        for (name in entryMetadata.keys) {
            if (name == "checksums.json") continue
            if (!reportedChecksums.containsKey(name)) return BackupValidationResult.Invalid("Entry $name missing from checksums.json")
        }

        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid("Missing data/database.json")
        val dbDto = try { json.decodeFromString<BackupSnapshotDto>(dbJson) } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed data/database.json") }

        val actualCounts = createTableMetadata(dbDto)
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (actualCounts[tableName]?.entryCount != metadata.entryCount) return BackupValidationResult.Invalid("Entry count mismatch for table: $tableName")
        }

        for (attachment in manifest.attachments) {
            val actual = entryMetadata[attachment.archivePath] ?: return BackupValidationResult.Invalid("Missing attachment file: ${attachment.archivePath}")
            if (actual.size != attachment.sizeBytes) return BackupValidationResult.Invalid("Attachment size mismatch: ${attachment.archivePath}")
            if (actual.checksum != attachment.checksumSha256) return BackupValidationResult.Invalid("Attachment checksum mismatch: ${attachment.archivePath}")
            if (attachment.referencedBy.isEmpty()) return BackupValidationResult.Invalid("Orphan attachment: ${attachment.attachmentId}")
        }

        return BackupValidationResult.Valid(manifest)
    }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
