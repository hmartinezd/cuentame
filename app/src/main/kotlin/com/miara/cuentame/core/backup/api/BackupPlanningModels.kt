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
    ) = create(sourceUri, attachmentId, archivePath, displayName, mimeType, sizeBytes, checksumSha256, references)

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
            return PlannedBackupAttachment(
                sourceUri = sourceUri,
                attachmentId = attachmentId,
                archivePath = archivePath,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksumSha256 = checksumSha256,
                _references = references.toList()
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

    fun copy(
        snapshotDto: BackupSnapshotDto = this.snapshotDto,
        snapshotJson: ImmutableBackupBytes = this.snapshotJson,
        preferencesDto: BackupPreferencesDto = this.preferencesDto,
        preferencesJson: ImmutableBackupBytes = this.preferencesJson,
        attachments: List<PlannedBackupAttachment> = this._attachments,
        manifest: BackupManifest = this.manifest,
        manifestJson: ImmutableBackupBytes = this.manifestJson,
        expectedEntryChecksums: Map<String, String> = this._expectedEntryChecksums,
        checksumsJson: ImmutableBackupBytes = this.checksumsJson,
        totalUncompressedBytes: Long = this.totalUncompressedBytes
    ) = BackupPlan(
        snapshotDto, snapshotJson, preferencesDto, preferencesJson,
        attachments.toList(), manifest, manifestJson, expectedEntryChecksums.toMap(),
        checksumsJson, totalUncompressedBytes
    )

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
            
            return BackupPlan(
                snapshotDto = snapshotDto,
                snapshotJson = ImmutableBackupBytes.from(snapshotJson),
                preferencesDto = preferencesDto,
                preferencesJson = ImmutableBackupBytes.from(preferencesJson),
                _attachments = attachments.toList(),
                manifest = manifest.copy(
                    tableMetadata = Collections.unmodifiableMap(manifest.tableMetadata.toMap()),
                    attachments = manifest.attachments.map { it.copy(referencedBy = it.referencedBy.toList()) }.toList(),
                    includedSections = manifest.includedSections.toList()
                ),
                manifestJson = ImmutableBackupBytes.from(manifestJson),
                _expectedEntryChecksums = expectedEntryChecksums.toMap(),
                checksumsJson = ImmutableBackupBytes.from(checksumsJson),
                totalUncompressedBytes = totalUncompressedBytes
            )
        }
    }
}
