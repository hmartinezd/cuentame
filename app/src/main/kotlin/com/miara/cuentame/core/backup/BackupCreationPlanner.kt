package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCreationPlanner @Inject constructor(
    private val localeReconciler: AppLocaleReconciler,
    private val preferencesSource: BackupPreferencesSource,
    private val attachmentSource: BackupAttachmentSource,
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider,
    private val jsonCodecs: BackupJsonCodecs
) {

    suspend fun createPlan(
        restaurant: Restaurant,
        snapshotResult: BackupSnapshotResult
    ): BackupPlanningResult {
        try {
            // 1. Reconcile locale
            val reconciliation = localeReconciler.reconcile()
            if (reconciliation is LocaleReconciliationResult.Failure || reconciliation is LocaleReconciliationResult.RestaurantNotFound) {
                return failure(BackupPlanningFailure.LocaleReconciliationFailed)
            }

            // 2. Reload preferences after reconciliation
            val preferencesDto = try {
                preferencesSource.loadPreferences()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return failure(BackupPlanningFailure.PreferencesReadFailed)
            }

            // 3. Verify locales
            if (SupportedAppLocale.fromLanguageTag(restaurant.localeTag) == null) {
                return failure(BackupPlanningFailure.UnsupportedRestaurantLocale)
            }
            if (SupportedAppLocale.fromLanguageTag(preferencesDto.appLocaleTag) == null) {
                return failure(BackupPlanningFailure.UnsupportedPreferencesLocale)
            }
            if (restaurant.localeTag != preferencesDto.appLocaleTag) {
                return failure(BackupPlanningFailure.PreferencesLocaleMismatch)
            }

            // 4. Validate theme
            try {
                ThemeMode.valueOf(preferencesDto.themeMode)
            } catch (e: Exception) {
                return failure(BackupPlanningFailure.InvalidPreferences)
            }

            // 5. Snapshot Grouping and Validation
            val snapshotDto = snapshotResult.dto
            val attachmentRefs = mutableMapOf<String, MutableList<BackupAttachmentReference>>()

            snapshotDto.purchaseReceipts.forEach { r ->
                r.attachmentId?.let { id ->
                    attachmentRefs.getOrPut(id) { mutableListOf() }.add(BackupAttachmentReference("PURCHASE_RECEIPT", r.id))
                }
            }
            snapshotDto.wasteEvents.forEach { w ->
                w.attachmentId?.let { id ->
                    attachmentRefs.getOrPut(id) { mutableListOf() }.add(BackupAttachmentReference("WASTE_EVENT", w.id))
                }
            }

            if (attachmentRefs.size > BackupLimits.MAX_ATTACHMENT_COUNT) return failure(BackupPlanningFailure.AttachmentLimitExceeded)

            val idToBindings = snapshotResult.attachmentBindings.groupBy { it.attachmentId }

            for (id in attachmentRefs.keys) {
                val bindings = idToBindings[id] ?: return failure(BackupPlanningFailure.MissingAttachmentSource)
                if (bindings.map { it.sourceUri }.distinct().size > 1) {
                    return failure(BackupPlanningFailure.ConflictingAttachmentSource)
                }
            }

            // 6. Table metadata from snapshot
            val tableMetadata = createTableMetadata(snapshotDto)

            // 7. Base manifest
            val baseManifest = BackupManifest(
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
                tableMetadata = tableMetadata,
                attachments = emptyList(),
                includedSections = listOf("data", "preferences", "attachments").sorted(),
                checksumAlgorithm = "SHA-256"
            )

            // 8. Snapshot integrity
            BackupSnapshotIntegrityValidator.validate(snapshotDto, baseManifest).getOrElse {
                return failure(BackupPlanningFailure.InvalidSnapshot)
            }

            // 9. Attachments and Serialization
            val entryChecksums = LinkedHashMap<String, String>()
            var currentTotalUncompressedBytes = 0L

            val snapshotJson = try {
                jsonCodecs.writer.encodeToString(snapshotDto).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (snapshotJson.size > BackupLimits.MAX_DATABASE_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            entryChecksums["data/database.json"] = computeSha256(snapshotJson)
            currentTotalUncompressedBytes += snapshotJson.size

            val preferencesJson = try {
                jsonCodecs.writer.encodeToString(preferencesDto).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            entryChecksums["preferences/settings.json"] = computeSha256(preferencesJson)
            currentTotalUncompressedBytes += preferencesJson.size

            val plannedAttachments = mutableListOf<PlannedBackupAttachment>()

            for ((id, refs) in attachmentRefs) {
                val uri = idToBindings[id]!!.first().sourceUri
                
                val metadata = try {
                    attachmentSource.inspect(uri)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { return failure(BackupPlanningFailure.UnreadableAttachment) }

                val sanitizedName = AttachmentFilenameSanitizer.sanitize(metadata.displayName)
                if (sanitizedName.isBlank()) return failure(BackupPlanningFailure.InvalidAttachmentMetadata)
                val archivePath = "attachments/$id/$sanitizedName"
                if (!ArchiveEntryValidator.isSafe(archivePath)) return failure(BackupPlanningFailure.InvalidAttachmentMetadata)
                if (archivePath.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return failure(BackupPlanningFailure.EntryNameLimitExceeded)

                val (size, checksum) = try {
                    attachmentSource.open(uri).use { stream ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var n: Int
                        while (stream.read(buffer).also { n = it } != -1) {
                            digest.update(buffer, 0, n)
                            totalRead += n
                            if (currentTotalUncompressedBytes + totalRead > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                return failure(BackupPlanningFailure.TotalSizeLimitExceeded)
                            }
                        }
                        totalRead to digest.digest().joinToString("") { "%02x".format(it) }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { return failure(BackupPlanningFailure.UnreadableAttachment) }

                entryChecksums[archivePath] = checksum
                currentTotalUncompressedBytes += size

                plannedAttachments.add(
                    PlannedBackupAttachment(
                        sourceUri = uri,
                        attachmentId = id,
                        archivePath = archivePath,
                        displayName = sanitizedName,
                        mimeType = metadata.mimeType,
                        sizeBytes = size,
                        checksumSha256 = checksum,
                        references = refs.sortedWith(compareBy({ it.recordType }, { it.recordId }))
                    )
                )
            }

            val finalManifest = baseManifest.copy(
                attachments = plannedAttachments.sortedBy { it.attachmentId }.map { att ->
                    BackupAttachmentMetadata(
                        attachmentId = att.attachmentId,
                        archivePath = att.archivePath,
                        displayName = att.displayName,
                        mimeType = att.mimeType,
                        sizeBytes = att.sizeBytes,
                        checksumSha256 = att.checksumSha256,
                        referencedBy = att.references
                    )
                }
            )

            val manifestJson = try {
                jsonCodecs.writer.encodeToString(finalManifest).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (manifestJson.size > BackupLimits.MAX_MANIFEST_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            entryChecksums["manifest.json"] = computeSha256(manifestJson)
            currentTotalUncompressedBytes += manifestJson.size

            val sortedChecksums = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
            val checksumsJson = try {
                val serializer = MapSerializer(String.serializer(), String.serializer())
                jsonCodecs.writer.encodeToString(serializer, sortedChecksums.toSortedMap()).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (checksumsJson.size > BackupLimits.MAX_CHECKSUMS_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            
            // Recompute checksum of checksums.json self? No, contract says checksums.json doesn't contain its own hash.
            // But writer MUST verify planned checksumsJson size correctly.
            currentTotalUncompressedBytes += checksumsJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return failure(BackupPlanningFailure.TotalSizeLimitExceeded)

            if (2 + plannedAttachments.size + 2 > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return failure(BackupPlanningFailure.JsonLimitExceeded)

            return BackupPlanningResult.Success(
                BackupPlan(
                    snapshotDto = snapshotDto,
                    snapshotJson = snapshotJson,
                    preferencesDto = preferencesDto,
                    preferencesJson = preferencesJson,
                    attachments = plannedAttachments.sortedBy { it.attachmentId },
                    manifest = finalManifest,
                    manifestJson = manifestJson,
                    expectedEntryChecksums = sortedChecksums,
                    checksumsJson = checksumsJson,
                    totalUncompressedBytes = currentTotalUncompressedBytes
                )
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(BackupPlanningFailure.UnexpectedPlanningFailure)
        }
    }

    private fun failure(reason: BackupPlanningFailure): BackupPlanningResult = BackupPlanningResult.Failure(reason)

    private fun computeSha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createTableMetadata(dto: com.miara.cuentame.core.backup.model.BackupSnapshotDto): Map<String, TableMetadata> = mapOf(
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
}
