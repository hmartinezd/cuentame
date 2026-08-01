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
            // 0. Schema check
            if (appVersionProvider.databaseSchemaVersion != BackupFormatV1Contract.DATABASE_SCHEMA_VERSION) {
                return failure(BackupPlanningFailure.UnsupportedDatabaseSchema)
            }

            // 0.1 Attachment check (V1 Policy: No attachments allowed)
            val snapshotDto = snapshotResult.dto
            if (snapshotDto.purchaseReceipts.any { it.attachmentId != null } ||
                snapshotDto.wasteEvents.any { it.attachmentId != null } ||
                snapshotResult.attachmentBindings.isNotEmpty()) {
                return failure(BackupPlanningFailure.AttachmentsNotSupported)
            }

            // 1. Reconcile locale
            val reconciliation = localeReconciler.reconcile()
            if (reconciliation is LocaleReconciliationResult.RestaurantNotFound) {
                return failure(BackupPlanningFailure.RestaurantDisappeared)
            }
            if (reconciliation is LocaleReconciliationResult.Failure) {
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

            // 5. Snapshot Grouping
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

            // 5.1 Validation of IDs and bindings
            val referencedIds = attachmentRefs.keys
            val bindingGroups = snapshotResult.attachmentBindings.groupBy { it.attachmentId }
            val bindingIds = bindingGroups.keys

            if (referencedIds != bindingIds) {
                val missing = referencedIds - bindingIds
                if (missing.isNotEmpty()) return failure(BackupPlanningFailure.MissingAttachmentSource)
                val extra = bindingIds - referencedIds
                if (extra.isNotEmpty()) return failure(BackupPlanningFailure.ExtraAttachmentSource)
            }

            for (id in referencedIds) {
                if (!BackupFormatV1Contract.isValidAttachmentId(id)) return failure(BackupPlanningFailure.InvalidAttachmentId)
                
                val group = bindingGroups[id]!!
                val distinctUris = group.map { it.sourceUri }.distinct()
                if (distinctUris.size > 1) {
                    return failure(BackupPlanningFailure.ConflictingAttachmentSource)
                }
            }

            if (attachmentRefs.size > BackupLimits.MAX_ATTACHMENT_COUNT) return failure(BackupPlanningFailure.AttachmentLimitExceeded)

            // 6. Table metadata from snapshot
            val tableMetadata = createTableMetadata(snapshotDto)

            // 7. Base manifest
            val baseManifest = BackupManifest(
                backupFormatVersion = BackupFormatV1Contract.BACKUP_FORMAT_VERSION,
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
                includedSections = BackupFormatV1Contract.REQUIRED_SECTIONS.toList().sorted(),
                checksumAlgorithm = BackupFormatV1Contract.CHECKSUM_ALGORITHM
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
            entryChecksums[BackupFormatV1Contract.DATABASE_ENTRY] = computeSha256(snapshotJson)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, snapshotJson.size.toLong())

            val preferencesJson = try {
                jsonCodecs.writer.encodeToString(preferencesDto).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            entryChecksums[BackupFormatV1Contract.PREFERENCES_ENTRY] = computeSha256(preferencesJson)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, preferencesJson.size.toLong())

            val plannedAttachments = mutableListOf<PlannedBackupAttachment>()

            for ((id, refs) in attachmentRefs) {
                val uri = bindingGroups[id]!!.first().sourceUri
                
                val metadata = try {
                    attachmentSource.inspect(uri)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { return failure(BackupPlanningFailure.UnreadableAttachment) }

                val sanitizedName = AttachmentFilenameSanitizer.sanitize(metadata.displayName)
                if (sanitizedName.isBlank()) return failure(BackupPlanningFailure.InvalidAttachmentMetadata)
                val archivePath = BackupFormatV1Contract.attachmentArchivePath(id, sanitizedName)
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
                            totalRead = BackupByteMath.addExact(totalRead, n.toLong())
                            val projectedTotal = BackupByteMath.addExact(currentTotalUncompressedBytes, totalRead)
                            if (projectedTotal > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                return failure(BackupPlanningFailure.TotalSizeLimitExceeded)
                            }
                        }
                        totalRead to digest.digest().joinToString("") { "%02x".format(it) }
                    }
                } catch (e: BackupSizeOverflowException) {
                    return failure(BackupPlanningFailure.TotalSizeLimitExceeded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return failure(BackupPlanningFailure.UnreadableAttachment)
                }

                entryChecksums[archivePath] = checksum
                currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, size)

                plannedAttachments.add(
                    PlannedBackupAttachment.create(
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
            entryChecksums[BackupFormatV1Contract.MANIFEST_ENTRY] = computeSha256(manifestJson)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, manifestJson.size.toLong())

            val sortedChecksums = entryChecksums.entries.sortedBy { it.key }.associate { it.key to it.value }
            val checksumsJson = try {
                val serializer = MapSerializer(String.serializer(), String.serializer())
                jsonCodecs.writer.encodeToString(serializer, sortedChecksums.toSortedMap()).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) { return failure(BackupPlanningFailure.SerializationFailed) }
            if (checksumsJson.size > BackupLimits.MAX_CHECKSUMS_JSON_BYTES) return failure(BackupPlanningFailure.JsonLimitExceeded)
            
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, checksumsJson.size.toLong())
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return failure(BackupPlanningFailure.TotalSizeLimitExceeded)

            val expectedEntryCount = 4 + plannedAttachments.size
            if (expectedEntryCount > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return failure(BackupPlanningFailure.ArchiveEntryCountExceeded)

            return BackupPlanningResult.Success(
                BackupPlan.create(
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

        } catch (e: BackupSizeOverflowException) {
            return failure(BackupPlanningFailure.TotalSizeLimitExceeded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(BackupPlanningFailure.UnexpectedPlanningFailure)
        }
    }

    private fun failure(reason: BackupPlanningFailure): BackupPlanningResult = BackupPlanningResult.Failure(reason)

    private fun computeSha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createTableMetadata(dto: com.miara.cuentame.core.backup.model.BackupSnapshotDto): Map<String, TableMetadata> {
        val tables = mutableMapOf(
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

        // Add recipe tables for schema 3
        if (appVersionProvider.databaseSchemaVersion >= 3) {
            tables["preparation_recipes"] = TableMetadata(dto.preparationRecipes.size, false)
            tables["preparation_recipe_components"] = TableMetadata(dto.preparationRecipeComponents.size, false)
        }

        return tables.entries.sortedBy { it.key }.associate { it.key to it.value }
    }
}
