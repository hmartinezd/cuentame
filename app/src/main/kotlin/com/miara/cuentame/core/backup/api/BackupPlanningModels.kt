package com.miara.cuentame.core.backup.api

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

    fun copy(
        sourceUri: AttachmentSourceUri = this.sourceUri,
        attachmentId: String = this.attachmentId,
        archivePath: String = this.archivePath,
        displayName: String = this.displayName,
        mimeType: String? = this.mimeType,
        sizeBytes: Long = this.sizeBytes,
        checksumSha256: String = this.checksumSha256,
        references: List<BackupAttachmentReference> = this._references
    ): PlannedBackupAttachment = create(
        sourceUri, attachmentId, archivePath, displayName, mimeType, sizeBytes, checksumSha256, references
    )

    companion object {
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
            require(archivePath == BackupFormatV1Contract.attachmentArchivePath(attachmentId, displayName)) {
                "Archive path must be canonical"
            }
            
            // Validate references
            val refKeys = references.map { 
                require(it.recordId.isNotBlank()) { "Reference record ID cannot be blank" }
                AttachmentReferenceKey(attachmentId, it.recordType, it.recordId) 
            }
            require(refKeys.distinct().size == refKeys.size) { "Duplicate references detected" }

            return PlannedBackupAttachment(
                sourceUri = sourceUri,
                attachmentId = attachmentId,
                archivePath = archivePath,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksumSha256 = checksumSha256,
                _references = references.map { it.copy() } // Deep copy
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

            // Recalculate total for verification
            var calculatedTotal = 0L
            calculatedTotal = BackupByteMath.addExact(calculatedTotal, sJson.size.toLong())
            calculatedTotal = BackupByteMath.addExact(calculatedTotal, pJson.size.toLong())
            attachments.forEach { calculatedTotal = BackupByteMath.addExact(calculatedTotal, it.sizeBytes) }
            calculatedTotal = BackupByteMath.addExact(calculatedTotal, mJson.size.toLong())
            calculatedTotal = BackupByteMath.addExact(calculatedTotal, cJson.size.toLong())
            
            require(calculatedTotal == totalUncompressedBytes) { 
                "Supplied total ($totalUncompressedBytes) does not match calculated total ($calculatedTotal)" 
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

            // Final deep copies
            return BackupPlan(
                snapshotDto = snapshotDto.copy(
                    restaurants = snapshotDto.restaurants.map { it.copy() },
                    inventoryAreas = snapshotDto.inventoryAreas.map { it.copy() },
                    ingredientCategories = snapshotDto.ingredientCategories.map { it.copy() },
                    units = snapshotDto.units.map { it.copy() },
                    ingredients = snapshotDto.ingredients.map { it.copy() },
                    ingredientUnitOptions = snapshotDto.ingredientUnitOptions.map { it.copy() },
                    suppliers = snapshotDto.suppliers.map { it.copy() },
                    purchaseReceipts = snapshotDto.purchaseReceipts.map { it.copy() },
                    purchaseLines = snapshotDto.purchaseLines.map { it.copy() },
                    stockCounts = snapshotDto.stockCounts.map { it.copy() },
                    stockCountAreas = snapshotDto.stockCountAreas.map { it.copy() },
                    stockCountLines = snapshotDto.stockCountLines.map { it.copy() },
                    wasteEvents = snapshotDto.wasteEvents.map { it.copy() },
                    inventoryMovements = snapshotDto.inventoryMovements.map { it.copy() },
                    inventoryBalanceProjections = snapshotDto.inventoryBalanceProjections.map { it.copy() },
                    ingredientCostProjections = snapshotDto.ingredientCostProjections.map { it.copy() }
                ),
                snapshotJson = sJson,
                preferencesDto = preferencesDto.copy(),
                preferencesJson = pJson,
                _attachments = attachments.map { it.copy() },
                manifest = manifest.copy(
                    tableMetadata = Collections.unmodifiableMap(manifest.tableMetadata.toMap()),
                    attachments = manifest.attachments.map { it.copy(referencedBy = it.referencedBy.map { r -> r.copy() }) },
                    includedSections = manifest.includedSections.toList()
                ),
                manifestJson = mJson,
                _expectedEntryChecksums = expectedEntryChecksums.toMap(),
                checksumsJson = cJson,
                totalUncompressedBytes = totalUncompressedBytes
            )
        }
    }
}
