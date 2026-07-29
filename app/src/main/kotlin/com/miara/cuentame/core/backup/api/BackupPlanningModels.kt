package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto

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
    data object UnreadableAttachment : BackupPlanningFailure
    data object InvalidAttachmentMetadata : BackupPlanningFailure
    data object AttachmentLimitExceeded : BackupPlanningFailure
    data object EntryNameLimitExceeded : BackupPlanningFailure
    data object TotalSizeLimitExceeded : BackupPlanningFailure
    data object JsonLimitExceeded : BackupPlanningFailure
    data object SerializationFailed : BackupPlanningFailure
    data object UnexpectedPlanningFailure : BackupPlanningFailure
}

data class PlannedBackupAttachment(
    val sourceUri: AttachmentSourceUri,
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val checksumSha256: String,
    val references: List<BackupAttachmentReference>
)

data class BackupPlan(
    val snapshotDto: BackupSnapshotDto,
    val snapshotJson: ImmutableBackupBytes,
    val preferencesDto: BackupPreferencesDto,
    val preferencesJson: ImmutableBackupBytes,
    val attachments: List<PlannedBackupAttachment>,
    val manifest: BackupManifest,
    val manifestJson: ImmutableBackupBytes,
    val expectedEntryChecksums: Map<String, String>,
    val checksumsJson: ImmutableBackupBytes,
    val totalUncompressedBytes: Long
) {
    constructor(
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
    ) : this(
        snapshotDto = snapshotDto,
        snapshotJson = ImmutableBackupBytes.from(snapshotJson),
        preferencesDto = preferencesDto,
        preferencesJson = ImmutableBackupBytes.from(preferencesJson),
        attachments = attachments.toList(),
        manifest = manifest,
        manifestJson = ImmutableBackupBytes.from(manifestJson),
        expectedEntryChecksums = expectedEntryChecksums.toMap(),
        checksumsJson = ImmutableBackupBytes.from(checksumsJson),
        totalUncompressedBytes = totalUncompressedBytes
    )
}
