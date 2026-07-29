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
    val snapshotJson: ByteArray,
    val preferencesDto: BackupPreferencesDto,
    val preferencesJson: ByteArray,
    val attachments: List<PlannedBackupAttachment>,
    val manifest: BackupManifest,
    val manifestJson: ByteArray,
    val expectedEntryChecksums: Map<String, String>,
    val checksumsJson: ByteArray,
    val totalUncompressedBytes: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BackupPlan
        if (snapshotDto != other.snapshotDto) return false
        if (!snapshotJson.contentEquals(other.snapshotJson)) return false
        if (preferencesDto != other.preferencesDto) return false
        if (!preferencesJson.contentEquals(other.preferencesJson)) return false
        if (attachments != other.attachments) return false
        if (manifest != other.manifest) return false
        if (!manifestJson.contentEquals(other.manifestJson)) return false
        if (expectedEntryChecksums != other.expectedEntryChecksums) return false
        if (!checksumsJson.contentEquals(other.checksumsJson)) return false
        if (totalUncompressedBytes != other.totalUncompressedBytes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = snapshotDto.hashCode()
        result = 31 * result + snapshotJson.contentHashCode()
        result = 31 * result + preferencesDto.hashCode()
        result = 31 * result + preferencesJson.contentHashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + manifest.hashCode()
        result = 31 * result + manifestJson.contentHashCode()
        result = 31 * result + expectedEntryChecksums.hashCode()
        result = 31 * result + checksumsJson.contentHashCode()
        result = 31 * result + totalUncompressedBytes.hashCode()
        return result
    }
}
