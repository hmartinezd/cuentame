package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.backup.platform.BackupStorageErrorClassifier
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupRepository @Inject constructor(
    private val snapshotSource: BackupSnapshotSource,
    private val attachmentSource: BackupAttachmentSource,
    private val documentStore: BackupDocumentStore,
    private val planner: BackupCreationPlanner,
    private val errorClassifier: BackupStorageErrorClassifier,
    private val restaurantRepository: RestaurantRepository,
    private val cleanupCoordinator: BackupCleanupCoordinator
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val DETERMINISTIC_ZIP_TIMESTAMP = 0L

    override fun createBackup(destinationUri: String): Flow<BackupOperationStatus> = flow {
        emit(BackupOperationStatus.Creating)
        val docUri = BackupDocumentUri(destinationUri)
        try {
            val restaurant = restaurantRepository.getRestaurant()
                ?: throw BackupCreationException(BackupResult.Error.RestaurantUnavailable)

            val snapshotResult = try {
                snapshotSource.loadSnapshot(restaurant.id.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure(e)) }

            val planningResult = planner.createPlan(restaurant, snapshotResult)
            if (planningResult is BackupPlanningResult.Failure) {
                throw BackupCreationException(mapPlanningFailure(planningResult.reason))
            }

            val plan = (planningResult as BackupPlanningResult.Success).plan

            documentStore.openForWrite(docUri).use { os ->
                BufferedOutputStream(os).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                        performBackup(zos, plan)
                    }
                }
            }

            emit(BackupOperationStatus.Validating)
            val validation = validateBackup(destinationUri)
            if (validation is BackupValidationResult.Valid) {
                emit(BackupOperationStatus.Success(validation.manifest))
            } else {
                val invalid = validation as BackupValidationResult.Invalid
                cleanupSafely(docUri)
                emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure(invalid.code, invalid.diagnostic)))
            }
        } catch (e: CancellationException) {
            cleanupSafely(docUri)
            throw e
        } catch (e: BackupCreationException) {
            cleanupSafely(docUri)
            emit(BackupOperationStatus.Error(e.error))
        } catch (e: Exception) {
            cleanupSafely(docUri)
            emit(BackupOperationStatus.Error(mapGeneralException(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun mapPlanningFailure(reason: BackupPlanningFailure): BackupResult.Error {
        return when (reason) {
            BackupPlanningFailure.LocaleReconciliationFailed -> BackupResult.Error.LocaleConsistencyFailure
            BackupPlanningFailure.UnsupportedRestaurantLocale -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.UnsupportedPreferencesLocale -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.PreferencesLocaleMismatch -> BackupResult.Error.LocaleConsistencyFailure
            BackupPlanningFailure.InvalidPreferences -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.InvalidSnapshot -> BackupResult.Error.DatabaseSnapshotFailure(Exception("Snapshot integrity validation failed"))
            BackupPlanningFailure.MissingAttachmentSource -> BackupResult.Error.MissingAttachment("unknown")
            BackupPlanningFailure.UnreadableAttachment -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.InvalidAttachmentMetadata -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.AttachmentLimitExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.JsonLimitExceeded -> BackupResult.Error.LimitExceeded
        }
    }

    private fun mapGeneralException(e: Exception): BackupResult.Error {
        val failure = errorClassifier.classify(e)
        return when (failure) {
            BackupStorageFailure.InsufficientSpace -> BackupResult.Error.InsufficientStorage
            BackupStorageFailure.PermissionDenied -> BackupResult.Error.PermissionDenied
            BackupStorageFailure.DestinationUnavailable -> BackupResult.Error.DestinationUnavailable
            BackupStorageFailure.GenericIo -> BackupResult.Error.SystemIOFailure(e)
        }
    }

    private suspend fun cleanupSafely(uri: BackupDocumentUri) {
        withContext(NonCancellable) {
            cleanupCoordinator.cleanup(uri)
        }
    }

    private suspend fun performBackup(
        zos: ZipOutputStream,
        plan: BackupPlan
    ) {
        val entryChecksums = mutableMapOf<String, String>()

        // 1. data/database.json
        writeZipEntry(zos, "data/database.json", plan.snapshotJson, entryChecksums)

        // 2. preferences/settings.json
        writeZipEntry(zos, "preferences/settings.json", plan.preferencesJson, entryChecksums)

        // 3. attachments
        val attachmentMetadatas = mutableListOf<BackupAttachmentMetadata>()

        for (att in plan.attachments) {
            attachmentSource.open(att.sourceUri).use { inputStream ->
                zos.putNextEntry(ZipEntry(att.archivePath).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
                
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var entrySize = 0L
                var n: Int
                while (inputStream.read(buffer).also { n = it } != -1) {
                    digest.update(buffer, 0, n)
                    zos.write(buffer, 0, n)
                    entrySize += n
                }
                zos.closeEntry()

                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                entryChecksums[att.archivePath] = checksum

                attachmentMetadatas.add(
                    BackupAttachmentMetadata(
                        attachmentId = att.attachmentId,
                        archivePath = att.archivePath,
                        displayName = att.displayName,
                        mimeType = att.mimeType,
                        sizeBytes = entrySize,
                        checksumSha256 = checksum,
                        referencedBy = att.references
                    )
                )
            }
        }

        // 4. manifest.json
        val finalManifest = plan.baseManifest.copy(
            tableMetadata = createTableMetadata(plan.snapshotDto),
            attachments = attachmentMetadatas.sortedBy { it.attachmentId }
        )
        val manifestJson = json.encodeToString(finalManifest).toByteArray(Charsets.UTF_8)
        writeZipEntry(zos, "manifest.json", manifestJson, entryChecksums)

        // 5. checksums.json
        val sortedChecksumMap = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
        val checksumsJson = json.encodeToString(sortedChecksumMap).toByteArray(Charsets.UTF_8)
        writeZipEntry(zos, "checksums.json", checksumsJson, null)
    }

    private fun writeZipEntry(
        zos: ZipOutputStream,
        name: String,
        content: ByteArray,
        checksums: MutableMap<String, String>?
    ) {
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        zos.write(content)
        zos.closeEntry()
        
        if (checksums != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(content)
            checksums[name] = digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun createTableMetadata(dto: com.miara.cuentame.core.backup.model.BackupSnapshotDto) = mapOf(
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
    ).entries.sortedBy { it.key }.associate { it.key to it.value }

    override suspend fun validateBackup(sourceUri: String): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            documentStore.openForRead(BackupDocumentUri(sourceUri)).use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    validateArchiveStream(zis)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR)
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
            if (totalEntries > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
            if (name.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH)

            if (entryMetadata.containsKey(name)) return BackupValidationResult.Invalid(BackupValidationCode.DUPLICATE_ENTRY)
            if (!ArchiveEntryValidator.isSafe(name)) return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH)

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
                if (currentTotalUncompressedSize > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)

                jsonBuffer?.write(buffer, 0, n)
                if (isJson) {
                    val limit = when(name) {
                        "manifest.json" -> BackupLimits.MAX_MANIFEST_JSON_BYTES
                        "preferences/settings.json" -> BackupLimits.MAX_SETTINGS_JSON_BYTES
                        "data/database.json" -> BackupLimits.MAX_DATABASE_JSON_BYTES
                        "checksums.json" -> BackupLimits.MAX_CHECKSUMS_JSON_BYTES
                        else -> 10 * 1024 * 1024 // 10MB fallback
                    }
                    if (entrySize > limit) return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            entryMetadata[name] = EntryInfo(name, checksum, entrySize)
            if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")

            zis.closeEntry()
            entry = zis.nextEntry
        }

        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val settingsJson = jsonPayloads["preferences/settings.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)

        // 1. Strict Manifest Validation
        val manifest = try {
            json.decodeFromString<BackupManifest>(manifestJson)
        } catch (e: Exception) { return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID) }

        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult is BackupValidationResult.Invalid) return manifestResult

        // 2. Exact Archive Entry Set
        val expectedBaseEntries = setOf("manifest.json", "checksums.json", "preferences/settings.json", "data/database.json")
        val attachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val totalExpectedEntries = expectedBaseEntries + attachmentPaths

        if (entryMetadata.keys != totalExpectedEntries) {
            return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        }

        // 3. Strict Checksums Validation
        val reportedChecksumsResult = ChecksumParser.parse(checksumsJson)
        if (reportedChecksumsResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
        }
        val reportedChecksums = reportedChecksumsResult.getOrThrow()

        if (reportedChecksums.size != entryMetadata.size - 1) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
        }

        for ((name, reportedChecksum) in reportedChecksums) {
            val actual = entryMetadata[name] ?: return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
            if (actual.checksum != reportedChecksum) return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH)
        }

        // 4. Preferences & Database Integrity
        val prefsDto = try {
            json.decodeFromString<BackupPreferencesDto>(settingsJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        }
        if (prefsDto.appLocaleTag !in SupportedAppLocale.languageTags) return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        if (prefsDto.appLocaleTag != manifest.localeTag) return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        
        val dbDto = try {
            json.decodeFromString<com.miara.cuentame.core.backup.model.BackupSnapshotDto>(dbJson)
        } catch (e: Exception) { return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID) }

        val actualCounts = createTableMetadata(dbDto)
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (actualCounts[tableName]?.entryCount != metadata.entryCount) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
            }
        }

        val integrityResult = BackupSnapshotIntegrityValidator.validate(dbDto, manifest)
        if (integrityResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, BackupValidationDiagnostic.SNAPSHOT_INTEGRITY_FAILURE)
        }

        // 5. Attachment consistency
        val manifestAttachments = manifest.attachments
        if (manifestAttachments.map { it.attachmentId }.distinct().size != manifestAttachments.size) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)

        for (attachment in manifestAttachments) {
            val actual = entryMetadata[attachment.archivePath] ?: return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            if (actual.size != attachment.sizeBytes) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_SIZE_MISMATCH)
            if (actual.checksum != attachment.checksumSha256) return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_CHECKSUM_MISMATCH)
        }

        return BackupValidationResult.Valid(manifest)
    }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
