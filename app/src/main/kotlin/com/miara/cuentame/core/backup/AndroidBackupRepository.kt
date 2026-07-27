package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    override suspend fun createBackup(destinationUri: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(destinationUri)
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: return@withContext BackupResult.Error.DestinationUnavailable
            
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
            
            val validation = validateBackup(destinationUri)
            if (validation is BackupValidationResult.Valid) {
                BackupResult.Success(validation.manifest)
            } else {
                BackupResult.Error.ArchiveValidationFailure((validation as BackupValidationResult.Invalid).reason)
            }
        } catch (e: BackupCreationException) {
            e.error
        } catch (e: SecurityException) {
            BackupResult.Error.PermissionDenied
        } catch (e: Exception) {
            BackupResult.Error.Unknown(e)
        }
    }

    private suspend fun performBackup(zos: ZipOutputStream, entryChecksums: MutableMap<String, String>) {
        val restaurant = restaurantRepository.getRestaurant() ?: throw BackupCreationException(BackupResult.Error.UnsupportedPersistentData)
        val snapshot = backupDao.createSnapshot(restaurant.id.value)
        val prefs = preferencesRepository.observePreferences().first()
        
        // 1. Identify and process attachments
        val uriToAttachmentId = mutableMapOf<String, String>()
        val attachmentIdToMetadata = mutableMapOf<String, BackupAttachmentMetadata>()
        
        fun addReference(uri: String, type: String, id: String) {
            if (uri.isBlank()) return
            val attachmentId = uriToAttachmentId.getOrPut(uri) { 
                java.util.UUID.nameUUIDFromBytes(uri.toByteArray()).toString() 
            }
            val metadata = attachmentIdToMetadata.getOrPut(attachmentId) {
                BackupAttachmentMetadata(
                    attachmentId = attachmentId,
                    archivePath = "", 
                    displayName = null,
                    mimeType = null,
                    sizeBytes = 0,
                    checksumSha256 = "",
                    referencedBy = mutableListOf()
                )
            }
            (metadata.referencedBy as MutableList).add(BackupAttachmentReference(type, id))
        }

        snapshot.purchaseReceipts.forEach { it.attachmentPath?.let { uri -> addReference(uri, "PURCHASE_RECEIPT", it.id) } }
        snapshot.wasteEvents.forEach { it.attachmentPath?.let { uri -> addReference(uri, "WASTE_EVENT", it.id) } }

        for ((uriString, metadata) in attachmentIdToMetadata) {
            val attachmentUri = Uri.parse(uriString)
            try {
                context.contentResolver.openInputStream(attachmentUri)?.use { inputStream ->
                    val displayName = queryDisplayName(attachmentUri)
                    val sanitizedName = ArchiveEntryValidator.sanitize(displayName ?: "file")
                    val archivePath = "attachments/${metadata.attachmentId}/$sanitizedName"
                    
                    if (!ArchiveEntryValidator.isSafe(archivePath)) {
                        throw Exception("Unsafe archive path: $archivePath")
                    }

                    val checksum = context.contentResolver.openInputStream(attachmentUri)!!.use { 
                        checksumProvider.calculateChecksum(it)
                    }
                    
                    zos.putNextEntry(ZipEntry(archivePath))
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (inputStream.read(buffer).also { n = it } != -1) {
                        size += n
                        zos.write(buffer, 0, n)
                    }
                    zos.closeEntry()
                    entryChecksums[archivePath] = checksum

                    attachmentIdToMetadata[metadata.attachmentId] = metadata.copy(
                        archivePath = archivePath,
                        displayName = displayName,
                        mimeType = context.contentResolver.getType(attachmentUri),
                        sizeBytes = size,
                        checksumSha256 = checksum
                    )
                } ?: throw Exception("Could not open attachment: $uriString")
            } catch (e: Exception) {
                throw BackupCreationException(BackupResult.Error.UnreadableAttachment(metadata.attachmentId, e))
            }
        }

        val snapshotDto = BackupMapper.mapToDto(snapshot, uriToAttachmentId)
        val snapshotJson = json.encodeToString(snapshotDto)
        
        // 2. Back up database entries
        writeZipEntry(zos, "data/database.json", snapshotJson, entryChecksums)
        
        // 3. Back up preferences
        val prefsMap = mapOf(
            "themeMode" to prefs.themeMode.name,
            "dynamicColorEnabled" to prefs.dynamicColorEnabled.toString(),
            "appLocaleTag" to prefs.appLocaleTag
        )
        writeZipEntry(zos, "preferences/settings.json", json.encodeToString(prefsMap), entryChecksums)
        
        // 4. Create Manifest
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
            tableMetadata = createTableMetadata(snapshotDto),
            attachments = attachmentIdToMetadata.values.toList().sortedBy { it.attachmentId },
            includedSections = listOf("data", "preferences", "attachments")
        )
        writeZipEntry(zos, "manifest.json", json.encodeToString(manifest), entryChecksums)
        
        // 5. Create Checksums
        val deterministicChecksums = entryChecksums.entries.sortedBy { it.key }
            .associate { it.key to it.value }
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
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
        if (name != "checksums.json") {
            checksums[name] = bytes.inputStream().use { checksumProvider.calculateChecksum(it) }
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

    override suspend fun validateBackup(sourceUri: String): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(sourceUri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    validateArchive(zis)
                }
            } ?: BackupValidationResult.Invalid("Could not open source URI")
        } catch (e: Exception) {
            BackupValidationResult.Invalid("Validation failed: ${e.message}")
        }
    }

    private fun validateArchive(zis: ZipInputStream): BackupValidationResult {
        val entries = mutableMapOf<String, ByteArray>()
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                if (entries.containsKey(entry.name)) {
                    return BackupValidationResult.Invalid("Duplicate entry: ${entry.name}")
                }
                if (!ArchiveEntryValidator.isSafe(entry.name)) {
                    return BackupValidationResult.Invalid("Unsafe entry name: ${entry.name}")
                }
                // Only read small JSON entries into memory for P1
                if (entry.name.endsWith(".json")) {
                    entries[entry.name] = zis.readBytes()
                } else if (entry.name.startsWith("attachments/")) {
                    // For attachments, we'll need to stream to validate checksum in a real robust validator.
                    // For P1, we'll read to memory if it fits, or skip if too large? 
                    // Let's at least read for now to complete the validation logic, 
                    // knowing it's a memory risk to be addressed in next phase hardening if needed.
                    entries[entry.name] = zis.readBytes()
                } else {
                    entries[entry.name] = zis.readBytes()
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestBytes = entries["manifest.json"] ?: return BackupValidationResult.Invalid("Missing manifest.json")
        val manifest = try {
            json.decodeFromString<BackupManifest>(manifestBytes.decodeToString())
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Malformed manifest.json")
        }

        if (manifest.backupFormatVersion != 1) {
            return BackupValidationResult.Invalid("Unsupported backup format version: ${manifest.backupFormatVersion}")
        }

        val checksumsBytes = entries["checksums.json"] ?: return BackupValidationResult.Invalid("Missing checksums.json")
        val reportedChecksums = try {
            json.decodeFromString<Map<String, String>>(checksumsBytes.decodeToString())
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Malformed checksums.json")
        }

        for ((name, reportedChecksum) in reportedChecksums) {
            val actualBytes = entries[name] ?: return BackupValidationResult.Invalid("Missing entry: $name")
            val actualChecksum = actualBytes.inputStream().use { checksumProvider.calculateChecksum(it) }
            if (actualChecksum != reportedChecksum) {
                return BackupValidationResult.Invalid("Checksum mismatch for $name")
            }
        }

        val dbBytes = entries["data/database.json"] ?: return BackupValidationResult.Invalid("Missing data/database.json")
        val dbDto = try {
            json.decodeFromString<BackupSnapshotDto>(dbBytes.decodeToString())
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Malformed data/database.json")
        }

        val actualCounts = createTableMetadata(dbDto)
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (actualCounts[tableName]?.entryCount != metadata.entryCount) {
                return BackupValidationResult.Invalid("Entry count mismatch for $tableName")
            }
        }

        return BackupValidationResult.Valid(manifest)
    }

    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
