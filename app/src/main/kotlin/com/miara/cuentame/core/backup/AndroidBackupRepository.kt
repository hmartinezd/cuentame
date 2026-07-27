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
import java.io.InputStream
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
            
            // Re-open in read mode to add checksums? 
            // Wait, SAF ACTION_CREATE_DOCUMENT might not allow opening for "rw" or "r" immediately?
            // Actually, we can just validate it by re-opening for "r".
            
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
        val snapshot = backupDao.createSnapshot()
        val prefs = preferencesRepository.observePreferences().first()
        
        val snapshotDto = BackupMapper.mapToDto(snapshot)
        val snapshotJson = json.encodeToString(snapshotDto)
        
        val attachmentMetadatas = mutableListOf<AttachmentMetadata>()
        
        // 1. Back up database entries
        writeZipEntry(zos, "data/database.json", snapshotJson, entryChecksums)
        
        // 2. Back up preferences
        val prefsMap = mapOf(
            "themeMode" to prefs.themeMode.name,
            "dynamicColorEnabled" to prefs.dynamicColorEnabled.toString(),
            "appLocaleTag" to prefs.appLocaleTag,
            "onboardingCompleted" to prefs.onboardingCompleted.toString()
        )
        writeZipEntry(zos, "preferences/settings.json", json.encodeToString(prefsMap), entryChecksums)
        
        // 3. Back up attachments
        val attachmentsToProcess = mutableSetOf<String>()
        snapshot.purchaseReceipts.mapNotNull { it.attachmentPath }.filter { it.isNotBlank() }.forEach { attachmentsToProcess.add(it) }
        snapshot.wasteEvents.mapNotNull { it.attachmentPath }.filter { it.isNotBlank() }.forEach { attachmentsToProcess.add(it) }
        
        for (uriString in attachmentsToProcess) {
            val attachmentUri = Uri.parse(uriString)
            try {
                context.contentResolver.openInputStream(attachmentUri)?.use { inputStream ->
                    val fileName = "attachments/${attachmentUri.lastPathSegment ?: "file_${uriString.hashCode()}"}"
                    
                    // We need to calculate checksum while streaming to avoid multiple opens if possible, 
                    // but ZipOutputStream doesn't give us back the content.
                    // Let's open twice if needed, or buffer. 
                    // For now, let's open twice to keep memory low.
                    val checksum = context.contentResolver.openInputStream(attachmentUri)!!.use { 
                        checksumProvider.calculateChecksum(it)
                    }
                    
                    zos.putNextEntry(ZipEntry(fileName))
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (inputStream.read(buffer).also { n = it } != -1) {
                        size += n
                        zos.write(buffer, 0, n)
                    }
                    zos.closeEntry()
                    entryChecksums[fileName] = checksum

                    attachmentMetadatas.add(AttachmentMetadata(
                        archivePath = fileName,
                        originalUri = uriString,
                        displayName = attachmentUri.lastPathSegment,
                        mimeType = context.contentResolver.getType(attachmentUri),
                        sizeBytes = size,
                        checksum = checksum
                    ))
                } ?: throw Exception("Could not open attachment: $uriString")
            } catch (e: Exception) {
                throw BackupCreationException(BackupResult.Error.UnreadableAttachment(uriString, e))
            }
        }
        
        // 4. Create Manifest
        val mainRestaurant = snapshot.restaurants.firstOrNull()
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(timeProvider.now()),
            applicationId = appVersionProvider.applicationId,
            appVersionName = appVersionProvider.versionName,
            appVersionCode = appVersionProvider.versionCode,
            databaseSchemaVersion = appVersionProvider.databaseSchemaVersion,
            restaurantId = mainRestaurant?.id,
            restaurantName = mainRestaurant?.name,
            localeTag = mainRestaurant?.localeTag,
            currencyCode = mainRestaurant?.currencyCode,
            tableMetadata = createTableMetadata(snapshotDto),
            attachments = attachmentMetadatas,
            preferences = prefsMap
        )
        writeZipEntry(zos, "manifest.json", json.encodeToString(manifest), entryChecksums)
        
        // 5. Create Checksums
        writeZipEntry(zos, "checksums.json", json.encodeToString(entryChecksums), mutableMapOf()) // Don't checksum the checksums file itself
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String, checksums: MutableMap<String, String>) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
        checksums[name] = bytes.inputStream().use { checksumProvider.calculateChecksum(it) }
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
                entries[entry.name] = zis.readBytes()
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

        // Verify all entries in manifest have correct checksums and exist
        for ((name, reportedChecksum) in reportedChecksums) {
            val actualBytes = entries[name] ?: return BackupValidationResult.Invalid("Missing entry: $name")
            val actualChecksum = actualBytes.inputStream().use { checksumProvider.calculateChecksum(it) }
            if (actualChecksum != reportedChecksum) {
                return BackupValidationResult.Invalid("Checksum mismatch for $name")
            }
        }

        // Verify table counts
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

