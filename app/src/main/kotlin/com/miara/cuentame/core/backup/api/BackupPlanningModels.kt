package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.ArchiveEntryValidator
import com.miara.cuentame.core.backup.AttachmentFilenameSanitizer
import com.miara.cuentame.core.backup.BackupLimits
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import java.util.Collections

sealed interface BackupPlanningResult {
    data class Success(
        val plan: BackupPlan
    ) : BackupPlanningResult

    data class Failure(
        val reason: BackupPlanningFailure
    ) : BackupPlanningResult
}

sealed interface BackupPlanningFailure {
    data object RestaurantDisappeared : BackupPlanningFailure
    data object LocaleReconciliationFailed : BackupPlanningFailure
    data object PreferencesReadFailed : BackupPlanningFailure
    data object UnsupportedRestaurantLocale : BackupPlanningFailure
    data object UnsupportedPreferencesLocale : BackupPlanningFailure
    data object PreferencesLocaleMismatch : BackupPlanningFailure
    data object InvalidPreferences : BackupPlanningFailure
    data object InvalidSnapshot : BackupPlanningFailure
    data object MissingAttachmentSource : BackupPlanningFailure
    data object ConflictingAttachmentSource : BackupPlanningFailure
    data object ExtraAttachmentSource : BackupPlanningFailure
    data object UnreadableAttachment : BackupPlanningFailure
    data object InvalidAttachmentMetadata : BackupPlanningFailure
    data object InvalidAttachmentId : BackupPlanningFailure
    data object AttachmentLimitExceeded : BackupPlanningFailure
    data object EntryNameLimitExceeded : BackupPlanningFailure
    data object TotalSizeLimitExceeded : BackupPlanningFailure
    data object ArchiveEntryCountExceeded : BackupPlanningFailure
    data object JsonLimitExceeded : BackupPlanningFailure
    data object SerializationFailed : BackupPlanningFailure
    data object UnexpectedPlanningFailure : BackupPlanningFailure
    data object UnsupportedDatabaseSchema : BackupPlanningFailure
    data object AttachmentsNotSupported : BackupPlanningFailure
}

class PlannedBackupAttachment private constructor(
    val sourceUri: AttachmentSourceUri,
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val checksumSha256: String,
    private val _references: List<BackupAttachmentReference>
) {
    val references: List<BackupAttachmentReference>
        get() = Collections.unmodifiableList(_references)

    companion object {
        fun deepCopyOf(
            attachment: PlannedBackupAttachment
        ): PlannedBackupAttachment = create(
            sourceUri = attachment.sourceUri,
            attachmentId = attachment.attachmentId,
            archivePath = attachment.archivePath,
            displayName = attachment.displayName,
            mimeType = attachment.mimeType,
            sizeBytes = attachment.sizeBytes,
            checksumSha256 = attachment.checksumSha256,
            references = attachment.references
        )

        fun create(
            sourceUri: AttachmentSourceUri,
            attachmentId: String,
            archivePath: String,
            displayName: String,
            mimeType: String?,
            sizeBytes: Long,
            checksumSha256: String,
            references: List<BackupAttachmentReference>
        ): PlannedBackupAttachment {
            require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
            require(BackupFormatV1Contract.isValidAttachmentId(attachmentId)) { "Invalid attachment ID format" }
            require(references.isNotEmpty()) { "Attachment must be referenced by at least one record" }
            require(displayName.isNotBlank()) { "Display name cannot be blank" }
            require(AttachmentFilenameSanitizer.isValid(displayName)) { "Invalid display name" }
            require(ArchiveEntryValidator.isSafe(archivePath)) { "Unsafe archive path" }
            
            require(archivePath == BackupFormatV1Contract.attachmentArchivePath(attachmentId, displayName)) {
                "Archive path must be canonical"
            }

            val nameBytes = archivePath.toByteArray(Charsets.UTF_8)
            require(nameBytes.size <= BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) { "Entry name exceeds limit" }

            require(BackupFormatV1Contract.isValidChecksum(checksumSha256)) { "Invalid checksum format" }
            
            val refKeys = mutableSetOf<AttachmentReferenceKey>()
            references.forEach { 
                require(it.recordId.isNotBlank()) { "Reference record ID cannot be blank" }
                require(it.recordType in BackupFormatV1Contract.SUPPORTED_ATTACHMENT_RECORD_TYPES) { "Unsupported record type: ${it.recordType}" }
                val key = AttachmentReferenceKey(attachmentId, it.recordType, it.recordId)
                require(refKeys.add(key)) { "Duplicate reference detected" }
            }

            return PlannedBackupAttachment(
                sourceUri = sourceUri,
                attachmentId = attachmentId,
                archivePath = archivePath,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksumSha256 = checksumSha256,
                _references = Collections.unmodifiableList(references.map { it.copy() })
            )
        }
    }
}

class BackupPlan private constructor(
    val snapshotDto: BackupSnapshotDto,
    val snapshotJson: ImmutableBackupBytes,
    val preferencesDto: BackupPreferencesDto,
    val preferencesJson: ImmutableBackupBytes,
    private val _attachments: List<PlannedBackupAttachment>,
    val manifest: BackupManifest,
    val manifestJson: ImmutableBackupBytes,
    private val _expectedEntryChecksums: Map<String, String>,
    val checksumsJson: ImmutableBackupBytes,
    val totalUncompressedBytes: Long
) {
    val attachments: List<PlannedBackupAttachment>
        get() = Collections.unmodifiableList(_attachments)

    val expectedEntryChecksums: Map<String, String>
        get() = Collections.unmodifiableMap(_expectedEntryChecksums)

    companion object {
        fun create(
            snapshotDto: BackupSnapshotDto,
            snapshotJson: ByteArray,
            preferencesDto: BackupPreferencesDto,
            preferencesJson: ByteArray,
            attachments: List<PlannedBackupAttachment>,
            manifest: BackupManifest,
            manifestJson: ByteArray,
            expectedEntryChecksums: Map<String, String>,
            checksumsJson: ByteArray,
            totalUncompressedBytes: Long
        ): BackupPlan {
            require(totalUncompressedBytes >= 0) { "totalUncompressedBytes must be non-negative" }
            
            val sJson = ImmutableBackupBytes.from(snapshotJson)
            val pJson = ImmutableBackupBytes.from(preferencesJson)
            val mJson = ImmutableBackupBytes.from(manifestJson)
            val cJson = ImmutableBackupBytes.from(checksumsJson)

            // Verify checksum values match planned payloads
            require(expectedEntryChecksums[BackupFormatV1Contract.DATABASE_ENTRY] == sJson.sha256()) {
                "Checksum mismatch for database entry"
            }
            require(expectedEntryChecksums[BackupFormatV1Contract.PREFERENCES_ENTRY] == pJson.sha256()) {
                "Checksum mismatch for preferences entry"
            }
            require(expectedEntryChecksums[BackupFormatV1Contract.MANIFEST_ENTRY] == mJson.sha256()) {
                "Checksum mismatch for manifest entry"
            }
            attachments.forEach { att ->
                require(expectedEntryChecksums[att.archivePath] == att.checksumSha256) {
                    "Checksum mismatch for attachment ${att.archivePath}"
                }
            }

            // Recalculate total for verification
            var recalculatedTotal = 0L
            recalculatedTotal = BackupByteMath.addExact(recalculatedTotal, sJson.size.toLong())
            recalculatedTotal = BackupByteMath.addExact(recalculatedTotal, pJson.size.toLong())
            attachments.forEach { recalculatedTotal = BackupByteMath.addExact(recalculatedTotal, it.sizeBytes) }
            recalculatedTotal = BackupByteMath.addExact(recalculatedTotal, mJson.size.toLong())
            recalculatedTotal = BackupByteMath.addExact(recalculatedTotal, cJson.size.toLong())
            
            require(recalculatedTotal == totalUncompressedBytes) { 
                "Supplied total ($totalUncompressedBytes) does not match calculated total ($recalculatedTotal)" 
            }

            // Verify attachment consistency
            val plannedIds = attachments.map { it.attachmentId }.toSet()
            require(plannedIds.size == attachments.size) { "Duplicate planned attachment IDs" }
            
            val manifestIds = manifest.attachments.map { it.attachmentId }.toSet()
            require(manifestIds.size == manifest.attachments.size) { "Duplicate manifest attachment IDs" }
            require(plannedIds == manifestIds) { "Attachment ID set mismatch between plan and manifest" }

            val plannedPaths = attachments.map { it.archivePath }.toSet()
            require(plannedPaths.size == attachments.size) { "Duplicate planned archive paths" }
            
            val manifestPaths = manifest.attachments.map { it.archivePath }.toSet()
            require(manifestPaths.size == manifest.attachments.size) { "Duplicate manifest archive paths" }
            require(plannedPaths == manifestPaths) { "Archive path set mismatch between plan and manifest" }

            // Reject duplicate references in planned and manifest attachments explicitly
            manifest.attachments.forEach { m ->
                val manifestKeys = m.referencedBy.map {
                    AttachmentReferenceKey(
                        attachmentId = m.attachmentId,
                        recordType = it.recordType,
                        recordId = it.recordId
                    )
                }
                require(manifestKeys.distinct().size == manifestKeys.size) {
                    "Duplicate manifest references"
                }
            }
            attachments.forEach { planned ->
                val plannedKeys = planned.references.map {
                    AttachmentReferenceKey(
                        attachmentId = planned.attachmentId,
                        recordType = it.recordType,
                        recordId = it.recordId
                    )
                }
                require(plannedKeys.distinct().size == plannedKeys.size) {
                    "Duplicate planned references"
                }
            }

            // Metadata agreement
            attachments.forEach { planned ->
                val m = manifest.attachments.find { it.attachmentId == planned.attachmentId }!!
                require(m.archivePath == planned.archivePath) { "Metadata mismatch: archivePath for ${planned.attachmentId}" }
                require(m.displayName == planned.displayName) { "Metadata mismatch: displayName for ${planned.attachmentId}" }
                require(m.mimeType == planned.mimeType) { "Metadata mismatch: mimeType for ${planned.attachmentId}" }
                require(m.sizeBytes == planned.sizeBytes) { "Metadata mismatch: sizeBytes for ${planned.attachmentId}" }
                require(m.checksumSha256 == planned.checksumSha256) { "Metadata mismatch: checksumSha256 for ${planned.attachmentId}" }
                
                val pRefs = planned.references.map { AttachmentReferenceKey(planned.attachmentId, it.recordType, it.recordId) }.toSet()
                val mRefs = m.referencedBy.map { AttachmentReferenceKey(planned.attachmentId, it.recordType, it.recordId) }.toSet()
                require(pRefs == mRefs) { "Metadata mismatch: references for ${planned.attachmentId}" }
            }

            // Verify checksum key set
            val expectedKeys = setOf(
                BackupFormatV1Contract.DATABASE_ENTRY,
                BackupFormatV1Contract.PREFERENCES_ENTRY,
                BackupFormatV1Contract.MANIFEST_ENTRY
            ) + plannedPaths
            
            require(expectedEntryChecksums.keys == expectedKeys) { "Expected checksum keys do not match planned entries" }
            require(!expectedEntryChecksums.containsKey(BackupFormatV1Contract.CHECKSUMS_ENTRY)) { "checksums.json must not be in the checksum map" }
            
            expectedEntryChecksums.values.forEach { 
                require(BackupFormatV1Contract.isValidChecksum(it)) { "Invalid expected checksum value" }
            }

            // Final deep unmodifiable copies
            return BackupPlan(
                snapshotDto = snapshotDto.copy(
                    restaurants = Collections.unmodifiableList(snapshotDto.restaurants.map { it.copy() }),
                    inventoryAreas = Collections.unmodifiableList(snapshotDto.inventoryAreas.map { it.copy() }),
                    ingredientCategories = Collections.unmodifiableList(snapshotDto.ingredientCategories.map { it.copy() }),
                    units = Collections.unmodifiableList(snapshotDto.units.map { it.copy() }),
                    ingredients = Collections.unmodifiableList(snapshotDto.ingredients.map { it.copy() }),
                    ingredientUnitOptions = Collections.unmodifiableList(snapshotDto.ingredientUnitOptions.map { it.copy() }),
                    suppliers = Collections.unmodifiableList(snapshotDto.suppliers.map { it.copy() }),
                    purchaseReceipts = Collections.unmodifiableList(snapshotDto.purchaseReceipts.map { it.copy() }),
                    purchaseLines = Collections.unmodifiableList(snapshotDto.purchaseLines.map { it.copy() }),
                    stockCounts = Collections.unmodifiableList(snapshotDto.stockCounts.map { it.copy() }),
                    stockCountAreas = Collections.unmodifiableList(snapshotDto.stockCountAreas.map { it.copy() }),
                    stockCountLines = Collections.unmodifiableList(snapshotDto.stockCountLines.map { it.copy() }),
                    wasteEvents = Collections.unmodifiableList(snapshotDto.wasteEvents.map { it.copy() }),
                    inventoryMovements = Collections.unmodifiableList(snapshotDto.inventoryMovements.map { it.copy() }),
                    inventoryBalanceProjections = Collections.unmodifiableList(snapshotDto.inventoryBalanceProjections.map { it.copy() }),
                    ingredientCostProjections = Collections.unmodifiableList(snapshotDto.ingredientCostProjections.map { it.copy() })
                ),
                snapshotJson = sJson,
                preferencesDto = preferencesDto.copy(),
                preferencesJson = pJson,
                _attachments = Collections.unmodifiableList(attachments.map { PlannedBackupAttachment.deepCopyOf(it) }),
                manifest = manifest.copy(
                    tableMetadata = Collections.unmodifiableMap(manifest.tableMetadata.toMap()),
                    attachments = Collections.unmodifiableList(manifest.attachments.map { it.copy(referencedBy = Collections.unmodifiableList(it.referencedBy.map { r -> r.copy() })) }),
                    includedSections = Collections.unmodifiableList(manifest.includedSections.toList())
                ),
                manifestJson = mJson,
                _expectedEntryChecksums = Collections.unmodifiableMap(expectedEntryChecksums.toMap()),
                checksumsJson = cJson,
                totalUncompressedBytes = totalUncompressedBytes
            )
        }
    }
}
