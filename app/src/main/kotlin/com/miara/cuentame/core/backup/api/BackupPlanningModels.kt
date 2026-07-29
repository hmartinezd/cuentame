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
            
            return PlannedBackupAttachment(
                sourceUri = sourceUri,
                attachmentId = attachmentId,
                archivePath = archivePath,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksumSha256 = checksumSha256,
                _references = references.toList() // Defensive copy
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
            val recalculatedTotal = sJson.size.toLong() +
                    pJson.size.toLong() +
                    attachments.sumOf { it.sizeBytes } +
                    mJson.size.toLong() +
                    cJson.size.toLong()
            
            require(recalculatedTotal == totalUncompressedBytes) { 
                "Supplied total ($totalUncompressedBytes) does not match calculated total ($recalculatedTotal)" 
            }

            // Verify attachment consistency
            val plannedIds = attachments.map { it.attachmentId }.toSet()
            val manifestIds = manifest.attachments.map { it.attachmentId }.toSet()
            require(plannedIds == manifestIds) { "Attachment ID set mismatch between plan and manifest" }

            val plannedPaths = attachments.map { it.archivePath }.toSet()
            val manifestPaths = manifest.attachments.map { it.archivePath }.toSet()
            require(plannedPaths == manifestPaths) { "Archive path set mismatch between plan and manifest" }

            // Verify checksum key set
            val expectedKeys = setOf(
                BackupFormatV1Contract.DATABASE_ENTRY,
                BackupFormatV1Contract.PREFERENCES_ENTRY,
                BackupFormatV1Contract.MANIFEST_ENTRY
            ) + plannedPaths
            
            require(expectedEntryChecksums.keys == expectedKeys) { "Expected checksum keys do not match planned entries" }
            require(!expectedEntryChecksums.containsKey(BackupFormatV1Contract.CHECKSUMS_ENTRY)) { "checksums.json must not be in the checksum map" }

            return BackupPlan(
                snapshotDto = snapshotDto,
                snapshotJson = sJson,
                preferencesDto = preferencesDto,
                preferencesJson = pJson,
                _attachments = attachments.toList(),
                manifest = manifest.copy(
                    tableMetadata = Collections.unmodifiableMap(manifest.tableMetadata.toMap()),
                    attachments = manifest.attachments.map { it.copy(referencedBy = it.referencedBy.toList()) }.toList(),
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
