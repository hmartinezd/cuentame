package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.entity.*
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

    companion object {
        private const val BACKUP_FORMAT_VERSION = 1
        private const val MAX_ARCHIVE_ENTRY_COUNT = 1000
        private const val MAX_JSON_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 500 * 1024 * 1024 // 500MB
        private const val MAX_ATTACHMENT_COUNT = 500
        private const val DETERMINISTIC_ZIP_TIMESTAMP = 0L
    }

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

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
                val reason = (validation as BackupValidationResult.Invalid).reason
                tryCleanupPartialFile(uri)
                emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure(reason)))
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
            if (e is java.io.IOException && e.message?.contains("ENOSPC", ignoreCase = true) == true) {
                emit(BackupOperationStatus.Error(BackupResult.Error.InsufficientStorage))
            } else {
                emit(BackupOperationStatus.Error(BackupResult.Error.Unknown(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun tryCleanupPartialFile(uri: Uri) {
        try {
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { it.close() }
        } catch (e: Exception) {
            // Best effort
        }
    }

    private suspend fun performBackup(zos: ZipOutputStream, entryChecksums: MutableMap<String, String>) {
        val restaurant = restaurantRepository.getRestaurant() ?: throw BackupCreationException(BackupResult.Error.UnsupportedPersistentData)
        val snapshot = try {
            backupDao.createSnapshot(restaurant.id.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure(e))
        }
        
        val prefs = try {
            preferencesRepository.observePreferences().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BackupCreationException(BackupResult.Error.PreferencesReadFailure(e))
        }
        
        // 1. Identify attachments
        val pendingAttachments = mutableMapOf<String, PendingAttachment>() // URI string -> PendingAttachment
        
        fun addReference(uriString: String?, type: String, recordId: String) {
            if (uriString.isNullOrBlank()) return
            val pending = pendingAttachments.getOrPut(uriString) {
                PendingAttachment(
                    sourceUri = Uri.parse(uriString),
                    attachmentId = java.util.UUID.nameUUIDFromBytes(uriString.toByteArray()).toString(),
                    references = mutableListOf()
                )
            }
            pending.references.add(BackupAttachmentReference(type, recordId))
        }

        snapshot.purchaseReceipts.forEach { addReference(it.attachmentPath, "PURCHASE_RECEIPT", it.id) }
        snapshot.wasteEvents.forEach { addReference(it.attachmentPath, "WASTE_EVENT", it.id) }

        if (pendingAttachments.size > MAX_ATTACHMENT_COUNT) {
            throw BackupCreationException(BackupResult.Error.ArchiveValidationFailure("Too many attachments: ${pendingAttachments.size}"))
        }

        // 2. data/database.json
        val uriToAttachmentId = pendingAttachments.mapValues { it.value.attachmentId }
        val snapshotDto = BackupMapper.mapToDto(snapshot, uriToAttachmentId)
        val snapshotJson = json.encodeToString(snapshotDto)
        if (snapshotJson.length > MAX_JSON_SIZE_BYTES) throw BackupCreationException(BackupResult.Error.SerializationFailure(Exception("Database JSON too large")))
        writeZipEntry(zos, "data/database.json", snapshotJson, entryChecksums)
        
        // 3. preferences/settings.json
        val prefsDto = BackupPreferencesDto(
            themeMode = prefs.themeMode.name,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            appLocaleTag = prefs.appLocaleTag
        )
        val prefsJson = json.encodeToString(prefsDto)
        writeZipEntry(zos, "preferences/settings.json", prefsJson, entryChecksums)
        
        // 4. attachments (sorted by ID)
        val attachmentMetadatas = mutableListOf<BackupAttachmentMetadata>()
        val sortedPending = pendingAttachments.values.sortedBy { it.attachmentId }
        
        for (pending in sortedPending) {
            try {
                context.contentResolver.openInputStream(pending.sourceUri)?.use { inputStream ->
                    val originalDisplayName = queryDisplayName(pending.sourceUri)
                    val effectiveDisplayName = AttachmentFilenameSanitizer.sanitize(originalDisplayName)
                    val archivePath = "attachments/${pending.attachmentId}/$effectiveDisplayName"
                    
                    if (!ArchiveEntryValidator.isSafe(archivePath)) {
                        throw Exception("Unsafe archive path generated")
                    }

                    zos.putNextEntry(ZipEntry(archivePath).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
                    
                    val digest = MessageDigest.getInstance("SHA-256")
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
            } catch (e: Exception) {
                if (e is BackupCreationException) throw e
                throw BackupCreationException(BackupResult.Error.UnreadableAttachment(pending.attachmentId, e))
            }
        }
        
        // 5. Create Manifest
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
        val manifestJson = json.encodeToString(manifest)
        writeZipEntry(zos, "manifest.json", manifestJson, entryChecksums)
        
        // 6. checksums.json
        val sortedChecksums = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
        val checksumsJson = json.encodeToString(sortedChecksums)
        writeZipEntry(zos, "checksums.json", checksumsJson, mutableMapOf())
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

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String, checksums: MutableMap<String, String>) {
        val bytes = content.toByteArray(Charsets.UTF_8)
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
            } ?: BackupValidationResult.Invalid("Could not open source URI")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            BackupValidationResult.Invalid("Validation failed: ${e.message}")
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
            if (totalEntries > MAX_ARCHIVE_ENTRY_COUNT) return BackupValidationResult.Invalid("Archive entry count limit exceeded")
            if (name.length > 255) return BackupValidationResult.Invalid("Archive entry name too long: $name")
            
            if (entryMetadata.containsKey(name)) return BackupValidationResult.Invalid("Duplicate entry: $name")
            if (!ArchiveEntryValidator.isSafe(name)) return BackupValidationResult.Invalid("Unsafe entry name: $name")

            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(8192)
            val jsonBuffer = if (name.endsWith(".json")) ByteArrayOutputStream() else null
            
            var n: Int
            while (zis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
                size += n
                totalUncompressedSize += n
                if (totalUncompressedSize > MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupValidationResult.Invalid("Total uncompressed size limit exceeded")
                
                jsonBuffer?.write(buffer, 0, n)
                if (jsonBuffer != null && size > MAX_JSON_SIZE_BYTES) return BackupValidationResult.Invalid("JSON payload too large: $name")
            }
            
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            entryMetadata[name] = EntryInfo(name, checksum, size)
            if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")
            
            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid("Missing manifest.json")
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid("Missing checksums.json")
        val settingsJson = jsonPayloads["preferences/settings.json"] ?: return BackupValidationResult.Invalid("Missing preferences/settings.json")
        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid("Missing data/database.json")
        
        // 1. Exact archive entry set for format v1
        val expectedBaseEntries = setOf("manifest.json", "checksums.json", "preferences/settings.json", "data/database.json")
        val manifest = try { json.decodeFromString<BackupManifest>(manifestJson) } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed manifest.json") }
        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult.isFailure) return BackupValidationResult.Invalid("Manifest invalid: ${manifestResult.exceptionOrNull()?.message}")

        val attachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val totalExpectedEntries = expectedBaseEntries + attachmentPaths
        
        if (entryMetadata.keys != totalExpectedEntries) {
            val unexpected = entryMetadata.keys - totalExpectedEntries
            val missing = totalExpectedEntries - entryMetadata.keys
            return BackupValidationResult.Invalid("Archive entry set mismatch. Unexpected: $unexpected, Missing: $missing")
        }

        // 2. Strict Checksums validation (Duplicate key rejection via manual scan)
        try { 
            // detect duplicates by re-parsing keys manually
            val decodedKeysFound = mutableSetOf<String>()
            val keyRegex = Regex("\"([^\"]+)\"\\s*:")
            val keyMatches = keyRegex.findAll(checksumsJson)
            for (match in keyMatches) {
                val key = match.groupValues[1]
                val decodedKey = json.decodeFromString<String>("\"$key\"")
                if (!decodedKeysFound.add(decodedKey)) return BackupValidationResult.Invalid("Duplicate key in checksums.json: $decodedKey")
            }
            
            val reportedChecksums = json.decodeFromString<Map<String, String>>(checksumsJson)
            if (reportedChecksums.size != decodedKeysFound.size) return BackupValidationResult.Invalid("Duplicate keys in checksums.json (logical mismatch)")

            for ((name, reportedChecksum) in reportedChecksums) {
                if (name == "checksums.json") return BackupValidationResult.Invalid("checksums.json cannot checksum itself")
                val actual = entryMetadata[name] ?: return BackupValidationResult.Invalid("Checksum for nonexistent entry: $name")
                if (actual.checksum != reportedChecksum) return BackupValidationResult.Invalid("Checksum mismatch for $name")
                if (!reportedChecksum.matches(Regex("^[a-f0-9]{64}$"))) return BackupValidationResult.Invalid("Invalid SHA-256 checksum format: $name")
            }

            for (name in entryMetadata.keys) {
                if (name == "checksums.json") continue
                if (!reportedChecksums.containsKey(name)) return BackupValidationResult.Invalid("Entry $name missing from checksums.json")
            }
        } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed checksums.json") }

        // 3. Preferences Validation
        val prefsDto = try {
            json.decodeFromString<BackupPreferencesDto>(settingsJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Invalid preferences/settings.json")
        }
        if (prefsDto.appLocaleTag !in setOf("en-US", "es-US")) return BackupValidationResult.Invalid("Unsupported app locale: ${prefsDto.appLocaleTag}")
        try {
            com.miara.cuentame.core.preferences.model.ThemeMode.valueOf(prefsDto.themeMode)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Unsupported theme mode: ${prefsDto.themeMode}")
        }

        // 4. Database Integrity
        val dbDto = try { json.decodeFromString<BackupSnapshotDto>(dbJson) } catch (e: Exception) { return BackupValidationResult.Invalid("Malformed data/database.json") }
        
        // Map DTO to BackupSnapshot for Integrity Validator
        val snapshot = BackupSnapshot(
            restaurants = dbDto.restaurants.map { RestaurantEntity(it.id, it.name, it.currencyCode, it.localeTag, it.createdAt, it.updatedAt, it.deletedAt) },
            inventoryAreas = dbDto.inventoryAreas.map { InventoryAreaEntity(it.id, it.restaurantId, it.name, it.normalizedName, it.sortOrder, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) },
            ingredientCategories = dbDto.ingredientCategories.map { IngredientCategoryEntity(it.id, it.restaurantId, it.name, it.normalizedName, it.sortOrder, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) },
            units = dbDto.units.map { UnitEntity(it.id, it.name, it.symbol, it.dimension, java.math.BigDecimal(it.factorToCanonical), it.isSystem, it.sortOrder) },
            ingredients = dbDto.ingredients.map { IngredientEntity(it.id, it.restaurantId, it.name, it.normalizedName, it.categoryId, it.baseUnitId, it.defaultAreaId, it.sku, it.notes, it.reorderPointBase?.let { rb -> java.math.BigDecimal(rb) }, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) },
            ingredientUnitOptions = dbDto.ingredientUnitOptions.map { IngredientUnitOptionEntity(it.id, it.ingredientId, it.displayName, it.shortLabel, it.standardUnitId, java.math.BigDecimal(it.factorToBase), it.isBase, it.isDefaultCount, it.isDefaultPurchase, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) },
            suppliers = dbDto.suppliers.map { SupplierEntity(it.id, it.restaurantId, it.name, it.normalizedName, it.phone, it.email, it.notes, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) },
            purchaseReceipts = dbDto.purchaseReceipts.map { PurchaseReceiptEntity(it.id, it.restaurantId, it.supplierId, it.invoiceNumber, it.purchaseDate, it.status, it.notes, null, it.createdAt, it.updatedAt, it.postedAt, it.voidedAt) },
            purchaseLines = dbDto.purchaseLines.map { PurchaseLineEntity(it.id, it.purchaseReceiptId, it.ingredientId, it.areaId, it.ingredientUnitOptionId, it.quantityEntered, it.quantityBase, it.unitCostBase, it.lineTotal, it.notes, it.createdAt, it.updatedAt) },
            stockCounts = dbDto.stockCounts.map { StockCountEntity(it.id, it.restaurantId, it.name, it.startedAt, it.effectiveAt, it.completedAt, it.status, it.notes, it.createdAt, it.updatedAt, it.voidedAt) },
            stockCountAreas = dbDto.stockCountAreas.map { StockCountAreaEntity(it.id, it.stockCountId, it.areaId, it.status, it.startedAt, it.completedAt, it.sortOrder) },
            stockCountLines = dbDto.stockCountLines.map { StockCountLineEntity(it.id, it.stockCountAreaId, it.ingredientId, it.ingredientUnitOptionId, it.quantityEntered, it.quantityBase, it.expectedQuantityBaseSnapshot, it.adjustmentQuantityBase, it.notes, it.createdAt, it.updatedAt) },
            wasteEvents = dbDto.wasteEvents.map { WasteEventEntity(it.id, it.restaurantId, it.ingredientId, it.areaId, it.ingredientUnitOptionId, it.quantityEntered, it.quantityBase, it.reason, it.effectiveAt, it.notes, null, it.status, it.createdAt, it.updatedAt, it.postedAt, it.voidedAt) },
            inventoryMovements = dbDto.inventoryMovements.map { InventoryMovementEntity(it.id, it.restaurantId, it.ingredientId, it.areaId, it.movementType, it.quantityBaseSigned, it.unitCostBaseSnapshot, it.totalValueSnapshot, it.effectiveAt, it.sourceDocumentType, it.sourceDocumentId, it.sourceOperationId, it.sourceLineId, it.reversalOfMovementId, it.createdAt) },
            inventoryBalanceProjections = dbDto.inventoryBalanceProjections.map { InventoryBalanceProjectionEntity(it.restaurantId, it.ingredientId, it.areaId, it.quantityBase, it.updatedAt) },
            ingredientCostProjections = dbDto.ingredientCostProjections.map { IngredientCostProjectionEntity(it.restaurantId, it.ingredientId, it.averageUnitCostBase, it.updatedAt) }
        )
        
        val integrityResult = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        if (integrityResult.isFailure) return BackupValidationResult.Invalid("Snapshot integrity failed: ${integrityResult.exceptionOrNull()?.message}")

        // 5. Attachment relationships (Set matching)
        val expectedRefs = mutableSetOf<String>()
        dbDto.purchaseReceipts.forEach { r -> r.attachmentId?.let { expectedRefs.add("$it|PURCHASE_RECEIPT|${r.id}") } }
        dbDto.wasteEvents.forEach { w -> w.attachmentId?.let { expectedRefs.add("$it|WASTE_EVENT|${w.id}") } }

        val manifestRefs = manifest.attachments.flatMap { a -> a.referencedBy.map { "${a.attachmentId}|${it.recordType}|${it.recordId}" } }.toSet()
        
        if (expectedRefs != manifestRefs) {
            val missing = expectedRefs - manifestRefs
            val extra = manifestRefs - expectedRefs
            return BackupValidationResult.Invalid("Attachment reference mismatch. Missing: $missing, Extra: $extra")
        }

        val manifestAttIds = manifest.attachments.map { it.attachmentId }.toSet()
        val dbAttIds = (dbDto.purchaseReceipts.mapNotNull { it.attachmentId } + dbDto.wasteEvents.mapNotNull { it.attachmentId }).toSet()
        if (manifestAttIds != dbAttIds) return BackupValidationResult.Invalid("Attachment ID set mismatch between manifest and database")

        for (attachment in manifest.attachments) {
            if (!AttachmentFilenameSanitizer.isValid(attachment.displayName ?: "")) {
                return BackupValidationResult.Invalid("Invalid attachment display name: ${attachment.displayName}")
            }
            val actual = entryMetadata[attachment.archivePath] ?: return BackupValidationResult.Invalid("Missing attachment file: ${attachment.archivePath}")
            if (actual.size != attachment.sizeBytes) return BackupValidationResult.Invalid("Attachment size mismatch: ${attachment.archivePath}")
            if (actual.checksum != attachment.checksumSha256) return BackupValidationResult.Invalid("Attachment checksum mismatch: ${attachment.archivePath}")
            
            val expectedPath = "attachments/${attachment.attachmentId}/${attachment.displayName}"
            if (attachment.archivePath != expectedPath) return BackupValidationResult.Invalid("Mismatched attachment path: ${attachment.archivePath}")
        }

        return BackupValidationResult.Valid(manifest)
    }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
